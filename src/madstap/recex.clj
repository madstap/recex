(ns madstap.recex
  (:require
   [cljc.java-time.day-of-week :as day-of-week]
   [cljc.java-time.zoned-date-time :as zoned-date-time]
   [cljc.java-time.offset-date-time :as offset-date-time]
   [cljc.java-time.instant :as instant]
   [clojure.spec.alpha :as s]
   [tick.core :as t]
   [time-specs.core :as ts]))

(s/def ::inner-recex
  (s/cat :time (s/or :set (s/coll-of ts/local-time? :kind set?)
                     :single ts/local-time?)
         :tz (s/? ts/zone-id?)))

(s/def ::recex
  (s/or :set (s/coll-of ::inner-recex :kind set?)
        :single ::inner-recex))

(defn normalize-inner [conformed-recex]
  (let [{tz :tz
         [time-type time] :time
         :or {tz (t/zone "UTC")}}
        conformed-recex]
    {:times (if (= :single time-type) #{time} time)
     :tz tz}))

(defn normalize [recex]
  (let [[recex-type inner] (s/conform ::recex recex)]
    (into #{} (map normalize-inner) (if (= :single recex-type) #{inner} inner))))

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

(defn indexed [coll]
  (map-indexed vector coll))

(defn interleave-time-seqs
  "Takes sequences of instants in time and returns a single lazy sequence
  of the times ordered chronologically. Assumes that each of the arguments
  are ordered chronologically."
  [& ts]
  (when-some [seqs (not-empty (vec (keep seq ts)))]
    (lazy-seq
     (let [[idx [t]] (apply min-key
                            #(instant/to-epoch-milli (t/instant (first (second %))))
                            (indexed seqs))]
       (cons t (apply interleave-time-seqs (update seqs idx rest)))))))

(defn iterate-days [t]
  (iterate #(t/+ % (t/new-period 1 :days)) t))

(defn times-of-day [now times tz]
  (->> times
       (map #(iterate-days (first-time now % tz)))
       (apply interleave-time-seqs)))

(defn inner-times [now {:keys [times tz]}]
  (times-of-day now times tz))

(defn times
  ([recex]
   (times (t/now) recex))
  ([now recex]
   (->> (normalize recex) (map #(inner-times now %)) (apply interleave-time-seqs))))
