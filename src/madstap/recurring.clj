(ns madstap.recurring
  (:require
   [cljc.java-time.day-of-week :as day-of-week]
   [cljc.java-time.zoned-date-time :as zoned-date-time]
   [cljc.java-time.offset-date-time :as offset-date-time]
   [clojure.spec.alpha :as s]
   [tick.core :as t]
   [time-specs.core :as ts]))

(s/def ::recex
  (s/cat :time ts/local-time?
         :tz (s/? ts/zone-id?)))

(defn first-date
  "Given an instant now and a time, return the next date that contains the time."
  [now time]
  (let [date (t/date now)]
    (if (t/< time (t/time now))
      (t/inc date)
      date)))

(defn first-time
  [now time tz]
  (let [date (first-date now time)]
    (if (ts/zone-offset? tz)
      (offset-date-time/of date time tz)
      (zoned-date-time/of date time tz))))

(defn iterate-days [t]
  (iterate #(t/+ % (t/new-period 1 :days)) t))

(defn times
  ([recex]
   (times (t/now) recex))
  ([now recex]
   (let [{:keys [time tz]
          :or {tz (t/zone "UTC")}}
         (s/conform ::recex recex)
         fst (first-time now time tz)]
     (iterate-days fst))))
