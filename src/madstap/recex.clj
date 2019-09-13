(ns madstap.recex
  (:require
   [cljc.java-time.instant :as instant]
   [cljc.java-time.local-date-time :as date-time]
   [cljc.java-time.local-time :as time]
   [cljc.java-time.zone-id :as zone-id]
   [cljc.java-time.zoned-date-time :as zoned-date-time]
   [clojure.spec.alpha :as s]
   [clojure.set :as set]
   [medley.core :as medley]
   [tick.core :as t]
   [time-specs.core :as ts])
  (:import (java.time.zone ZoneOffsetTransition ZoneRules)))

(defn str* [x]
  (if (ident? x) (name x) (str x)))

 (def parse-month
  (comp t/parse-month str*))

(s/def ::month*
  (s/and (s/conformer parse-month) ts/month?))

(s/def ::month
  (s/and (s/or :range (s/coll-of (s/tuple ::month* ::month*))
               :one ::month*)
         (s/conformer second)))

(def parse-dow
  (comp t/parse-day str*))

(s/def ::dow
  (s/and (s/conformer parse-dow) ts/day-of-week?))

(s/def ::day-of-week
  (s/or :day-of-week
        (s/and (s/or :dow ::dow
                     :range (s/coll-of (s/tuple ::dow ::dow)))
               (s/conformer second))
        :nth-day-of-week
        (s/and vector?
               (s/cat :n (s/nonconforming
                          (s/or :pos (s/int-in 1 (inc 5))
                                :neg (s/int-in -5 (inc -1))))
                      :day ::dow))))

(s/def ::day-of-month*
  (s/nonconforming
   (s/or :pos (s/int-in 1 (inc 31))
         :neg (s/int-in -31 (inc -1)))))

(s/def ::day-of-month
  (s/nonconforming
   (s/or :range (s/coll-of (s/tuple ::day-of-month* ::day-of-month*))
         :one ::day-of-month*)))

(defn parse-time [x]
  (cond (ts/local-time? x) x
        (string? x) (try (t/parse x) (catch Exception _ nil))
        :else nil))

(defn normalize-set [x]
  (if (set? x)
    (into #{} (mapcat normalize-set) x)
    #{x}))

(defmacro nested-set-or-one-of [spec]
  `(s/and (s/conformer normalize-set) (s/coll-of ~spec :kind set?)))

(s/def ::h-int (s/int-in 0 24))
(s/def ::m-int (s/int-in 0 60))
(s/def ::s-int (s/int-in 0 60))

(s/def ::h
  (nested-set-or-one-of
   (s/nonconforming
    (s/or :range (s/map-of ::h-int ::h-int)
          :int ::h-int))))

(s/def ::m
  (nested-set-or-one-of
   (s/nonconforming
    (s/or :range (s/map-of ::m-int ::m-int)
          :int ::m-int))))

(s/def ::s
  (nested-set-or-one-of
   (s/nonconforming
    (s/or :range (s/map-of ::s-int ::s-int)
          :int ::s-int))))

(s/def ::time-expr
  (s/keys :req-un [(or ::h ::m ::s)]))

(def unit->n
  {:h 24
   :m 60
   :s 60})

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
                 [(assoc exp unit (set (range (unit->n unit)))) true]
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
        (->> (-> t-expr fill-lesser-units fill-greater-units)
             (medley/map-vals normalize-set)
             (medley/map-vals expand-ranges))]
    (for [h hours, m minutes, s seconds]
      (time/of h m s))))

(defn expand-times [times]
  (into #{} (mapcat #(if (ts/local-time? %) #{%} (expand-time-expr %))) times))

(s/def ::time
  (s/and
   (s/or :time (s/and (s/conformer parse-time) ts/local-time?)
         :time-expr ::time-expr)
   (s/conformer second)))

(defn parse-tz [x]
  (cond (ts/zone-id? x) x
        (string? x) (try (t/zone x) (catch Exception _ nil))))

(s/def ::tz
  (s/and (s/conformer parse-tz) ts/zone-id?))

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
  (s/and
   (s/conformer #(when (map? %)
                   (set/rename-keys % {:dst/overlap :madstap.recex.dst/overlap
                                       :dst/gap :madstap.recex.dst/gap})))
   (s/keys :opt [:madstap.recex.dst/overlap
                 :madstap.recex.dst/gap])))

(s/def ::inner-recex
  (s/and vector?
         (s/cat :month (s/? (nested-set-or-one-of ::month))
                :day-of-week (s/? (nested-set-or-one-of ::day-of-week))
                :day-of-month (s/? (nested-set-or-one-of ::day-of-month))
                :time (s/? (nested-set-or-one-of ::time))
                :tz (s/? (nested-set-or-one-of ::tz))
                :dst-opts (s/? ::dst-opts))))

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

;; Added indirection so this doesn't break when making the switch to spec2
(defn valid? [recex]
  (s/valid? ::recex recex))

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

(defn days-in-month [t]
  (t/day-of-month (t/last-day-of-month t)))

(defn day-of-month-filter [day]
  (if (pos? day)
    (fn [time]
      (= day (t/day-of-month time)))
    (fn [time]
      (= (+ (inc (days-in-month time)) day)
         (t/day-of-month time)))))

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
          (medley/take-upto #(= date (t/date %)))
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
  ([recex]
   (times (t/now) recex))
  ([now recex]
   (->> (normalize recex)
        (map #(inner-times (t/instant now) %))
        (apply interleave-time-seqs))))
