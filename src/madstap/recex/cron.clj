(ns madstap.recex.cron
  (:require
   [madstap.recex :as recex]
   [medley.core :as medley]
   [clojure.string :as str]
   [tick.alpha.api :as t]))

;; https://crontab.guru
;; https://www.pantz.org/software/cron/croninfo.html
;; https://mangolassi.it/topic/9131/unix-scheduling-with-cron

(defn split-fields [cron]
  (let [fields (str/split cron #"\s+")]
    (zipmap (case (count fields)
              5 [   :m :h :day :month :day-of-week]
              6 [:s :m :h :day :month :day-of-week])
            fields)))

(defn split-list [s]
  (str/split s #","))

(defn split-step [s]
  (when-some [[_ base step] (re-find #"^([^/]+)?/([^/]+)$" s)]
    [base step]))

(defn split-range [s]
  (when-some [[_ from to] (re-find #"^([^\-]+)-([^\-]+)$" s)]
    [from to]))

(defn parse-int [s]
  (try (Integer/parseInt s)
       (catch Exception _)))

(defn filter-steps [step xs]
  (->> (medley/indexed xs)
       (keep (fn [[idx x]]
               (when (zero? (mod idx step))
                 x)))))

(def wildcard? (comp boolean #{"*"}))

(defn parse-month-scalar [s]
  (or (some-> (parse-int s) (t/month))
      (t/month s)))

(defn parse-dow-scalar [s]
  (if-some [i (parse-int s)]
    (if (zero? i) (t/day-of-week "SUN") (t/day-of-week i))
    (if (= "thu" s)
      (t/day-of-week "thursday")
      (t/day-of-week s))))

(defn to-int [x]
  (if (integer? x) x (t/int x)))

(defn parser [parse-scalar constructor max]
  (fn [s]
    (->> (split-list s)
         (map (fn [x]
                (if-let [[base step] (split-step x)]
                  (->> (if (nil? base)
                         (range 0 (inc max))
                         (if-let [[from to] (split-range base)]
                           (range (to-int (parse-scalar from))
                                  (inc (to-int (parse-scalar to))))
                           (range (to-int (parse-scalar base)) (inc max))))
                       (filter-steps (parse-int step))
                       (map constructor)
                       (set))
                  (if-let [[from to] (split-range x)]
                    {(parse-scalar from) (parse-scalar to)}
                    (parse-scalar x)))))
         (into #{})
         (recex/normalize-set))))

(def parse-dow
  (parser parse-dow-scalar t/day-of-week 7))

(def parse-month
  (parser parse-month-scalar t/month 12))

(def parse-day
  (parser parse-int identity 31))

(defn wrap-wildcard [f wildcard-val]
  (fn [s]
    (if (wildcard? s)
      wildcard-val
      (f s))))

(def parse-s
  (let [max-s (dec (recex/unit->n :s))]
    (-> (parser parse-int identity max-s)
        (wrap-wildcard {0 max-s}))))

(def parse-m
  (let [max-m (dec (recex/unit->n :m))]
    (-> (parser parse-int identity max-m)
        (wrap-wildcard {0 max-m}))))

(def parse-h
  (let [max-h (dec (recex/unit->n :h))]
    (-> (parser parse-int identity max-h)
        (wrap-wildcard {0 max-h}))))

(defn unwrap-simple [x]
  (if (and (set? x) (= 1 (count x))) (first x) x))

(defn time-expr [fields]
  (-> fields
      (select-keys [:s :m :h])
      (medley/update-existing :s (comp unwrap-simple parse-m))
      (update :m (comp unwrap-simple parse-m))
      (update :h (comp unwrap-simple parse-h))))

(defn unwrap-simple-slots [recex]
  (if (set? recex)
    (into #{} (map unwrap-simple-slots) recex)
    (into [] (map unwrap-simple) recex)))

(defn cron->recex
  ([cron tz]
   (-> (cron->recex cron) (conj tz)))
  ([cron]
   (let [{:keys [m h day month day-of-week] :as fields} (split-fields cron)
         time (time-expr fields)]
     (-> (case (mapv wildcard? [day month day-of-week])
           [true true true] [time]
           [false true true] [(parse-day day) time]
           [true false true] [(parse-month month) time]
           [true true false] [(parse-dow day-of-week) time]
           [false false true] [(parse-month month) (parse-day day) time]
           [true false false] [(parse-month month) (parse-dow day-of-week) time]
           [false true false] #{[(parse-day day) time]
                                [(parse-dow day-of-week) time]}
           [false false false] #{[(parse-month month) (parse-day day) time]
                                 [(parse-month month) (parse-dow day-of-week) time]})
         (unwrap-simple-slots)))))
