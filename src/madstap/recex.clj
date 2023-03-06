(ns madstap.recex
  (:require
   [cljc.java-time.day-of-week :as day-of-week]
   [cljc.java-time.instant :as instant]
   [cljc.java-time.local-date-time :as date-time]
   [cljc.java-time.local-time :as time]
   [cljc.java-time.month :as month]
   [cljc.java-time.zone-id :as zone-id]
   [cljc.java-time.zoned-date-time :as zoned-date-time]
   [clojure.math.combinatorics :as combo]
   [clojure.set :as set]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [madstap.recex.util :as util]
   [madstap.recex.cron :as cron]
   [medley.core :as medley]
   [tick.core :as t])
  (:import (java.time.zone ZoneOffsetTransition ZoneRules)))

(defn str* [x]
  (if (ident? x) (name x) (str x)))

(defn parse-month [x]
  (if (t/month? x)
    x
    (t/parse-month (str* x))))

(def all-months
  (map month/of (range 1 (inc 12))))

(defn month-gen []
  (s/gen (set all-months)))

(defn day-of-week-gen []
  (s/gen (set (map day-of-week/of (range 1 (inc 7))))))

(defn time-gen []
  (gen/fmap (fn [[h m s ns]]
              (time/of h m s ns))
            (gen/tuple (s/gen (s/int-in 0 24))
                       (s/gen (s/int-in 0 60))
                       (s/gen (s/int-in 0 60))
                       (s/gen (s/int-in 0 1000000000)))))

(defn zone-id-gen []
  (s/gen (into #{} (map zone-id/of) (zone-id/get-available-zone-ids))))

(defn instant-gen []
  (gen/fmap t/instant (s/gen inst?)))

(defn wrapping-range-of [spec]
  (s/and (s/map-of spec spec :min-count 1 :gen-max 1 :conform-keys true)
         #(every? (fn [[start end]] (not= start end)) %)))

(defn wrapping-range-or-one-of [spec]
  (s/and (s/or :one spec :range (wrapping-range-of spec))
         (s/conformer second)))

(defn range-of [spec]
  (s/and (wrapping-range-of spec)
         #(every? (fn [[start end]] (< start end)) %)))

(defn range-or-one-of [spec]
  (s/and (s/or :one spec :range (range-of spec))
         (s/conformer second)))

(s/def ::month*
  (s/with-gen (s/and (s/conformer parse-month) t/month?)
    month-gen))

(s/def ::month
  (wrapping-range-or-one-of ::month*))

(def parse-dow
  (comp t/parse-day str*))

(s/def ::dow
  (s/with-gen (s/and (s/conformer parse-dow) t/day-of-week?)
    day-of-week-gen))

(s/def ::pos-nth-day-of-week
  (s/int-in 1 (inc 5)))

(s/def ::neg-nth-day-of-week
  (s/int-in -5 (inc -1)))

(s/def ::simple-day-of-week
  (wrapping-range-or-one-of ::dow))

(s/def ::nth-day-of-week
  (s/with-gen (s/and vector?
                     (s/cat :n (s/nonconforming
                                (s/or :pos ::pos-nth-day-of-week
                                      :neg ::neg-nth-day-of-week))
                            :day ::dow))
    #(gen/tuple (gen/one-of (map s/gen [::pos-nth-day-of-week
                                        ::neg-nth-day-of-week]))
                (s/gen ::dow))))

(s/def ::day-of-week
  (s/or :day-of-week ::simple-day-of-week
        :nth-day-of-week ::nth-day-of-week))

(s/def ::day-of-month*
  (s/nonconforming
   (s/or :pos (s/int-in 1 (inc 31))
         :neg (s/int-in -31 (inc -1)))))

(s/def ::day-of-month
  (wrapping-range-or-one-of ::day-of-month*))

(defn parse-time [x]
  (cond (t/time? x) x
        (string? x) (try (t/time x) (catch Exception _ nil))))

(defn nested-set-or-one-of [spec]
  (let [set-spec (s/coll-of spec :kind set? :min-count 1)]
    (s/with-gen (s/and (s/conformer util/normalize-set) set-spec)
      #(gen/one-of [(s/gen spec) (s/gen set-spec)]))))

(s/def ::h-int (s/int-in 0 (util/unit->n :h)))
(s/def ::m-int (s/int-in 0 (util/unit->n :m)))
(s/def ::s-int (s/int-in 0 (util/unit->n :s)))

(s/def ::h
  (nested-set-or-one-of
   (range-or-one-of ::h-int)))

(s/def ::m
  (nested-set-or-one-of
   (range-or-one-of ::m-int)))

(s/def ::s
  (nested-set-or-one-of
   (range-or-one-of ::s-int)))

(s/def ::time-expr
  (s/and (s/keys :req-un [(or ::h ::m ::s)])
         #(set/subset? (set (keys %)) #{:h :m :s})))

(defn fill-lesser-units [t-expr]
  (first
   (reduce (fn [[exp fill?] unit]
             (if (unit exp)
               [exp true]
               (if fill?
                 [(assoc exp unit 0) true]
                 [exp false])))
           [t-expr false]
           [:h :m :s])))

(defn fill-greater-units [t-expr]
  (first
   (reduce (fn [[exp fill?] unit]
             (if (unit exp)
               [exp true]
               (if fill?
                 [(assoc exp unit (set (range (util/unit->n unit)))) true]
                 [exp false])))
           [t-expr false]
           [:s :m :h])))

(defn expand-range [rangee]
  (into #{} (mapcat (fn [[start end]]
                      (range start (inc end)))) rangee))

(defn expand-ranges [values]
  (into #{} (mapcat #(if (map? %) (expand-range %) #{%})) values))

(defn expand-time-expr [t-expr]
  (let [{hours :h, minutes :m, seconds :s}
        (-> t-expr
            fill-greater-units
            fill-lesser-units
            (update-vals util/normalize-set)
            (update-vals expand-ranges))]
    (for [h hours, m minutes, s seconds]
      (time/of h m s))))

(defn expand-times [times]
  (into #{} (mapcat #(if (t/time? %) #{%} (expand-time-expr %))) times))

(s/def ::time
  (s/and
   (s/or :time (s/with-gen (s/and (s/conformer parse-time) t/time?)
                 time-gen)
         :time-expr ::time-expr)
   (s/conformer second)))

(defn parse-tz [x]
  (cond (t/zone? x) x
        (string? x) (try (t/zone x) (catch Exception _ nil))))

(s/def ::tz
  (s/with-gen (s/and (s/conformer parse-tz) t/zone?)
    zone-id-gen))

(s/def :madstap.recex.dst/transition-type
  (s/nilable #{:overlap :gap}))

(defn dst-transition [tz date-time]
  (let [rules ^ZoneRules (zone-id/get-rules tz)]
    (when-some [^ZoneOffsetTransition tran (-> rules (.getTransition date-time))]
      (if (.isOverlap tran) :overlap :gap))))

(s/def :madstap.recex.dst/overlap
  #{:first :second :both})

(s/def :madstap.recex.dst/gap
  #{:skip :include})

(s/def ::dst-opts
  (let [keys-spec (s/keys :opt [:madstap.recex.dst/overlap
                                :madstap.recex.dst/gap])]
    (s/with-gen (s/and
                 map?
                 #(set/subset? (set (keys %)) #{:dst/overlap
                                                :madstap.recex.dst/overlap
                                                :dst/gap
                                                :madstap.recex.dst/gap})
                 (s/conformer #(when (map? %)
                                 (set/rename-keys % {:dst/overlap :madstap.recex.dst/overlap
                                                     :dst/gap :madstap.recex.dst/gap})))
                 keys-spec)
      #(s/gen keys-spec))))

(s/def ::inner-recex
  (let [re-spec (s/cat :month (s/? (nested-set-or-one-of ::month))
                       :day-of-week (s/? (nested-set-or-one-of ::day-of-week))
                       :day-of-month (s/? (nested-set-or-one-of ::day-of-month))
                       :time (s/? (nested-set-or-one-of ::time))
                       :tz (s/? (nested-set-or-one-of ::tz))
                       :dst-opts (s/? ::dst-opts))]
    (s/with-gen (s/and vector? re-spec)
      #(gen/fmap vec (s/gen re-spec)))))

(s/def ::recex
  (nested-set-or-one-of ::inner-recex))

(def dst-defaults
  {:madstap.recex.dst/overlap :first
   :madstap.recex.dst/gap :include})

(defn wrap-range [max start end]
  (let [xs (cycle (range 1 (inc max)))]
    (->> (nthnext xs (dec start))
         (medley/take-upto #{end}))))

(defn expand-dow-ranges [dows]
  (into #{}
        (mapcat (fn [[typee v :as dow]]
                  (if (and (= :day-of-week typee)
                           (map? v))
                    (->> (reduce (fn [acc [start end]]
                                   (->> (wrap-range 7 (t/int start) (t/int end))
                                        (map t/day-of-week)
                                        (into acc)))
                                 #{}, v)
                         (map (fn [d] [typee d])))
                    [dow])))
        dows))

(defn expand-month-ranges [months]
  (into #{}
        (mapcat #(if (map? %)
                   (reduce (fn [acc [start end]]
                             (->> (wrap-range 12 (t/int start) (t/int end))
                                  (map t/month)
                                  (into acc)))
                           #{}, %)
                   #{%}))
        months))

(defn normalize-inner [conformed-recex]
  (map #(-> conformed-recex
            (assoc :tz %)
            (update :month expand-month-ranges)
            (update :day-of-week expand-dow-ranges)
            (update :day-of-month expand-ranges)
            (update :time (fnil identity #{(t/time "00:00")}))
            (update :dst-opts (partial merge dst-defaults)))
       (:tz conformed-recex #{(t/zone "UTC")})))

(defn normalize [recex]
  (into #{} (mapcat normalize-inner) (s/conform ::recex recex)))

(def month->max-days
  {(t/month "JAN") 31
   (t/month "FEB") 29
   (t/month "MAR") 31
   (t/month "APR") 30
   (t/month "MAY") 31
   (t/month "JUN") 30
   (t/month "JUL") 31
   (t/month "AUG") 31
   (t/month "SEP") 30
   (t/month "OCT") 31
   (t/month "NOV") 30
   (t/month "DEC") 31})

(defn days-in-month [t]
  (t/day-of-month (t/last-day-of-month t)))

(defn neg-day-of-month [t]
  (dec (- (t/day-of-month t) (days-in-month t))))

(comment
  (time
   (->> all-months
        (medley/distinct-by month->max-days)
        (pmap (fn [m]
                [(month->max-days m)
                 (->> (concat (range -5 0) (range 1 (inc 5)))
                      (pmap (fn [n]
                              [n (->> (times [m [n :monday]])
                                      (mapcat (juxt t/day-of-month neg-day-of-month))
                                      (take 400)
                                      (distinct)
                                      (sort)
                                      (into (sorted-set)))]))
                      (into (sorted-map)))]))
        (into (sorted-map))))

  )

(def max-days->nth->possible-days
  {29
   {-5 #{-29 1},
    -4 #{-28 -27 -26 -25 -24 -23 -22 1 2 3 4 5 6 7 8},
    -3 #{-21 -20 -19 -18 -17 -16 -15 8 9 10 11 12 13 14 15},
    -2 #{-14 -13 -12 -11 -10 -9 -8 15 16 17 18 19 20 21 22},
    -1 #{-7 -6 -5 -4 -3 -2 -1 22 23 24 25 26 27 28 29},
    1 #{-29 -28 -27 -26 -25 -24 -23 -22 1 2 3 4 5 6 7},
    2 #{-22 -21 -20 -19 -18 -17 -16 -15 8 9 10 11 12 13 14},
    3 #{-15 -14 -13 -12 -11 -10 -9 -8 15 16 17 18 19 20 21},
    4 #{-8 -7 -6 -5 -4 -3 -2 -1 22 23 24 25 26 27 28},
    5 #{-1 29}},
   30
   {-5 #{-30 -29 1 2},
    -4 #{-28 -27 -26 -25 -24 -23 -22 3 4 5 6 7 8 9},
    -3 #{-21 -20 -19 -18 -17 -16 -15 10 11 12 13 14 15 16},
    -2 #{-14 -13 -12 -11 -10 -9 -8 17 18 19 20 21 22 23},
    -1 #{-7 -6 -5 -4 -3 -2 -1 24 25 26 27 28 29 30},
    1 #{-30 -29 -28 -27 -26 -25 -24 1 2 3 4 5 6 7},
    2 #{-23 -22 -21 -20 -19 -18 -17 8 9 10 11 12 13 14},
    3 #{-16 -15 -14 -13 -12 -11 -10 15 16 17 18 19 20 21},
    4 #{-9 -8 -7 -6 -5 -4 -3 22 23 24 25 26 27 28},
    5 #{-2 -1 29 30}},
   31
   {-5 #{-31 -30 -29 1 2 3},
    -4 #{-28 -27 -26 -25 -24 -23 -22 4 5 6 7 8 9 10},
    -3 #{-21 -20 -19 -18 -17 -16 -15 11 12 13 14 15 16 17},
    -2 #{-14 -13 -12 -11 -10 -9 -8 18 19 20 21 22 23 24},
    -1 #{-7 -6 -5 -4 -3 -2 -1 25 26 27 28 29 30 31},
    1 #{-31 -30 -29 -28 -27 -26 -25 1 2 3 4 5 6 7},
    2 #{-24 -23 -22 -21 -20 -19 -18 8 9 10 11 12 13 14},
    3 #{-17 -16 -15 -14 -13 -12 -11 15 16 17 18 19 20 21},
    4 #{-10 -9 -8 -7 -6 -5 -4 22 23 24 25 26 27 28},
    5 #{-3 -2 -1 29 30 31}}})

(defn impossible-month-day? [months days]
  (if (or (empty? months) (empty? days))
    false
    (not (some (fn [[m d]]
                 (<= (abs d) (month->max-days m)))
               (combo/cartesian-product months days)))))

(defn impossible-nth-day-of-week? [months dows days]
  (let [mnths (or (seq months) all-months)]
    (if (or (empty? dows) (empty? days))
      false
      (not (some (fn [[m [_ {:keys [n]}] d]]
                   (let [possible-days (get-in max-days->nth->possible-days
                                               [(month->max-days m) n])]
                     (contains? possible-days d)))
                 (combo/cartesian-product mnths dows days))))))

(defn impossible? [recex]
  (let [normalized (normalize recex)]
    (or (some (fn [{:keys [month day-of-month day-of-week]}]
                (let [nth-dows (filter #(= :nth-day-of-week (first %)) day-of-week)]
                  (or (impossible-month-day? month day-of-month)
                      (impossible-nth-day-of-week? month nth-dows day-of-month))))
              normalized)
        false)))

(defn explain-str [recex]
  (cond (not (s/valid? ::recex recex)) (s/explain-str ::recex recex)
        (impossible? recex) (str "Impossible combination of either month/day "
                                 "or month/nth-day-of-week/day.")))

(defn valid? [recex]
  (not (explain-str recex)))

(defn interleave-time-seqs
  "Takes sequences of instants in time and returns a single lazy sequence
  of the times ordered chronologically. Assumes that each of the arguments
  are ordered chronologically."
  [& ts]
  (when-some [seqs (not-empty (vec (keep seq ts)))]
    (lazy-seq
     (let [[idx [t]] (apply min-key
                            #(instant/to-epoch-milli (t/instant (first (second %))))
                            (medley/indexed seqs))]
       (cons t (apply interleave-time-seqs (update seqs idx rest)))))))

(defn first-date
  "Given an zoned or offset date time `now` and a time `time`,
  return the next date that contains the time."
  [now time]
  (let [date (t/date now)]
    (if (t/< time (t/time now))
      (t/inc date)
      date)))

(defn month-filter [month]
  (fn [time]
    (= month (t/month time))))

(defn day-of-month-filter [day]
  (if (pos? day)
    (fn [time]
      (= day (t/day-of-month time)))
    (fn [time]
      (= day (neg-day-of-month time)))))

(defn day-of-week-filter [day-of-week]
  (fn [time]
    (= day-of-week (t/day-of-week time))))

(defn days-of-week-in-month [t dow]
  (count
   (->> (iterate t/inc (t/first-day-of-month t))
        (take (days-in-month t))
        (filter (day-of-week-filter dow)))))

(defn nth-day-of-week
  "The weekday of t is which number in the month.

  Example: If today is 2019-09-30, that is the 5th monday of the month,
           so the function would return 5."
  [t]
  (let [date (t/date t)]
    (count
     (->> (iterate t/inc (t/first-day-of-month date))
          (medley/take-upto #{date})
          (filter (day-of-week-filter (t/day-of-week t)))))))

(defn nth-day-of-week-filter [n day-of-week]
  (every-pred
   (day-of-week-filter day-of-week)
   (if (pos? n)
     (fn [time]
       (= n (nth-day-of-week time)))
     (fn [time]
       (= (+ (inc (days-of-week-in-month time day-of-week)) n)
          (nth-day-of-week time))))))

(defn every-filter [& filters]
  (if-some [fs (not-empty (remove nil? filters))]
    (apply every-pred fs)
    (constantly true)))

(defn any-filter [& filters]
  (if-some [fs (not-empty (remove nil? filters))]
    (apply some-fn fs)
    (constantly true)))

(defn times-of-day [date times tz {:madstap.recex.dst/keys [overlap gap]}]
  (->> (map #(date-time/of date %) times)
       (mapcat (fn [dt]
                 (let [zdt (zoned-date-time/of dt tz)]
                   (if-some [transition-type (dst-transition tz dt)]
                     (case transition-type
                       :overlap
                       (case overlap
                         :first
                         [(zoned-date-time/with-earlier-offset-at-overlap zdt)]
                         :second
                         [(zoned-date-time/with-later-offset-at-overlap zdt)]
                         :both
                         [(zoned-date-time/with-earlier-offset-at-overlap zdt)
                          (zoned-date-time/with-later-offset-at-overlap zdt)])
                       :gap (case gap
                              :skip []
                              :include [zdt]))
                     [zdt]))))
       (sort)))

(defn inner-times [now {:keys [month day-of-week day-of-month time tz dst-opts]}]
  (let [zoned-now (t/in now tz)
        all-times (expand-times time)
        date-pred
        (every-filter
         (apply any-filter (map month-filter month))
         (apply any-filter (map (fn [[dow-type dow]]
                                  (case dow-type
                                    :day-of-week
                                    (day-of-week-filter dow)
                                    :nth-day-of-week
                                    (nth-day-of-week-filter (:n dow) (:day dow))))
                                day-of-week))
         (apply any-filter (map day-of-month-filter day-of-month)))]

    (sequence (comp (filter date-pred)
                    (mapcat #(times-of-day % all-times tz dst-opts))
                    (drop-while #(t/< % zoned-now)))
              (iterate t/inc (t/date zoned-now)))))

(defn times
  "Generate an infinite sequence of zoned date times from a recex.

  A recex is either a vector consisting of slots, all optional, or a
  set combining multiple vectors (which are conceptually OR'ed together).

  The slots are:
  [month day-of-week day-of-month time time-zone dst-options]

  The dst-options is an options map for choosing which way to deal with
  daylight saving time edge-cases and doesn't follow the same rules as
  the rest of the slots.

  The rest of the slots are conceptually AND'ed together, which means that
  they all need to match for each time generated, and in each slot there can
  be either a single value that must be matched or a set of values in which one
  of them must match (OR). The values can be either scalar values, which are
  either java.time objects or shorthands (keywords for months and days-of-week,
  strings for times and time zones, integer for day-of-month). A value can also
  be a range, which is a map of {from to} and is inclusive."
  ([recex]
   (times recex (t/now)))
  ([recex now]
   (when-some [s (explain-str recex)]
     (throw (ex-info s {:recex recex})))
   (->> (normalize recex)
        (map #(inner-times (t/instant now) %))
        (apply interleave-time-seqs))))

(def ^{:arglists '([cron tz] [cron])} cron->recex
  "Transforms a cron string to a recex. In adition to the standard cron syntax,
  there's also an optional second slot at the start and some of the extra
  features from the quartz cron dialect are supported."
  cron/cron->recex)
