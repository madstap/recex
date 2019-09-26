(ns madstap.recex.cron
  (:require
   [madstap.recex.util :as util]
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
  (next (re-find #"^([^/]+)?/([^/]+)$" s)))

(defn split-range [s]
  (next (re-find #"(?i)^([^\-^L]+)-([^\-]+)$" s)))

(defn parse-int [s]
  (try (Integer/parseInt s)
       (catch Exception _)))

(defn filter-steps [step xs]
  (->> (medley/indexed xs)
       (keep (fn [[idx x]]
               (when (zero? (mod idx step))
                 x)))))

(def wildcard? (comp boolean #{"*" "?"}))

(defn parse-month-scalar [s]
  (or (some-> (parse-int s) (t/month))
      (t/month s)))

(defn split-nth [s]
  (next (re-find #"^([^#]+)#([^#]+)$" s)))

(defn split-last [s]
  (if-some [[_ day n] (re-find #"(?i)^([^L]+)?L(-\d+)?" s)]
    [day (or (some-> n (subs 1)) "1")]))

(defn dow* [s]
  (if-some [i (parse-int s)]
    (if (zero? i) (t/day-of-week "SUN") (t/day-of-week i))
    (if (= "thu" (str/lower-case s))
      (t/day-of-week "thursday")
      (t/day-of-week s))))

(defn parse-day-scalar [s]
  (if-some [[_ n] (split-last s)]
    (- (parse-int n))
    (parse-int s)))

(defn parse-dow-scalar [s]
  (if-some [[day n] (split-nth s)]
    [(parse-int n) (dow* day)]
    (if-some [[day n] (split-last s)]
      [(- (parse-int n)) (dow* day)]
      (dow* s))))

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
         (util/normalize-set))))

(def parse-dow
  (parser parse-dow-scalar t/day-of-week 7))

(def parse-month
  (parser parse-month-scalar t/month 12))

(def parse-day
  (parser parse-day-scalar identity 31))

(defn wrap-wildcard [f wildcard-val]
  (fn [s]
    (if (wildcard? s)
      wildcard-val
      (f s))))

(def parse-s
  (let [max-s (dec (util/unit->n :s))]
    (-> (parser parse-int identity max-s)
        (wrap-wildcard {0 max-s}))))

(def parse-m
  (let [max-m (dec (util/unit->n :m))]
    (-> (parser parse-int identity max-m)
        (wrap-wildcard {0 max-m}))))

(def parse-h
  (let [max-h (dec (util/unit->n :h))]
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

(defn add-tz [recex tz]
  (if (set? recex)
    (into #{} (map #(add-tz % tz)) recex)
    (conj recex tz)))

(defn cron->recex
  ([cron tz]
   (-> (cron->recex cron) (add-tz tz)))
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
