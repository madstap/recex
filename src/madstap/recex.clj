(ns madstap.recex
  (:require
   [cljc.java-time.day-of-week :as day-of-week]
   [cljc.java-time.instant :as instant]
   [cljc.java-time.offset-date-time :as offset-date-time]
   [cljc.java-time.zoned-date-time :as zoned-date-time]
   [clojure.spec.alpha :as s]
   [medley.core :as medley]
   [tick.core :as t]
   [time-specs.core :as ts]))

(defn str* [x]
  (if (ident? x) (name x) (str x)))

 (def parse-month
  (comp t/parse-month str*))

(s/def ::month
  (s/and (s/conformer parse-month) ts/month?))

(def parse-dow
  (comp t/parse-day str*))

(s/def ::dow
  (s/and (s/conformer parse-dow) ts/day-of-week?))

(s/def ::day-of-week
  (s/or :day-of-week ::dow
        :nth-day-of-week
        (s/and vector?
               (s/cat :n (s/nonconforming
                          (s/or :pos (s/int-in 1 (inc 5))
                                :neg (s/int-in -5 (inc -1))))
                      :day ::dow))))

(s/def ::day-of-month
  (s/nonconforming
   (s/or :pos (s/int-in 1 (inc 31))
         :neg (s/int-in -31 (inc -1)))))

(defn parse-time [x]
  (cond (ts/local-time? x) x
        (string? x) (try (t/parse x) (catch Exception _ nil))
        :else nil))

(s/def ::time
  (s/and (s/conformer parse-time) ts/local-time?))

(defn parse-tz [x]
  (cond (ts/zone-id? x) x
        (string? x) (try (t/zone x) (catch Exception _ nil))
        :else nil))

(s/def ::tz
  (s/and (s/conformer parse-tz) ts/zone-id?))

(defn flatten-sets [x]
  (if (set? x)
    (into #{} (mapcat flatten-sets) x)
    #{x}))

(defmacro nested-set-or-one-of [spec]
  `(s/and (s/conformer flatten-sets) (s/coll-of ~spec :kind set?)))

(s/def ::inner-recex
  (s/and vector?
         (s/cat :month (s/? (nested-set-or-one-of ::month))
                :day-of-week (s/? (nested-set-or-one-of ::day-of-week))
                :day-of-month (s/? (nested-set-or-one-of ::day-of-month))
                :time (s/? (nested-set-or-one-of ::time))
                :tz (s/? (nested-set-or-one-of ::tz)))))

(s/def ::recex
  (nested-set-or-one-of ::inner-recex))

(defn normalize-inner [conformed-recex]
  (map #(-> conformed-recex
            (assoc :tz %)
            (update :time (fnil identity #{(t/time "00:00")})))
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

(defn at-zone [inst tz]
  (if (ts/zone-offset? tz)
    (offset-date-time/with-offset-same-instant (t/offset-date-time inst) tz)
    (zoned-date-time/with-zone-same-instant (t/zoned-date-time inst) tz)))

(defn first-time
  [now time tz]
  (let [date (-> now (at-zone tz) (first-date time))]
    (if (ts/zone-offset? tz)
      (offset-date-time/of date time tz)
      (zoned-date-time/of date time tz))))

(defn inc-day [t]
  (t/+ t (t/new-period 1 :days)))

(defn times-of-day [now times tz]
  (->> times
       (map #(iterate inc-day (first-time now % tz)))
       (apply interleave-time-seqs)))

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
   (->> (iterate inc-day (t/first-day-of-month t))
        (take (days-in-month t))
        (filter (day-of-week-filter dow)))))

(defn nth-day-of-week
  "The weekday of t is which number in the month.

  Example: If today is 2019-09-30, that is the 5th monday of the month,
           so the function would return 5."
  [t]
  (let [date (t/date t)]
    (count
     (->> (iterate inc-day (t/first-day-of-month t))
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

(defn inner-times [now {:keys [month day-of-week day-of-month time tz]}]
  (let [pred (every-filter
              (apply any-filter (map month-filter month))
              (apply any-filter (map (fn [[dow-type dow]]
                                       (case dow-type
                                         :day-of-week
                                         (day-of-week-filter dow)
                                         :nth-day-of-week
                                         (nth-day-of-week-filter (:n dow) (:day dow))))
                                     day-of-week))
              (apply any-filter (map day-of-month-filter day-of-month)))]
    (sequence (filter pred) (times-of-day now time tz))))

(defn times
  ([recex]
   (times (t/now) recex))
  ([now recex]
   (->> (normalize recex) (map #(inner-times now %)) (apply interleave-time-seqs))))
