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
  (zipmap [:m :h :day :month :day-of-week] (str/split cron #"\s+")))

(defn split-list [s]
  (str/split s #","))

(defn split-step [s]
  (when-some [[_ base step] (re-find #"^([^/]+)/([^/]+)$" s)]
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

(defn int-parser [max]
  (fn [s]
    (if (wildcard? s)
      {0 (dec max)}
      (->> (split-list s)
           (map (fn [x]
                  (if-let [[base step] (split-step x)]
                    (->> (if-let [[from to] (split-range base)]
                           (range (parse-int from) (inc (parse-int to)))
                           (range (parse-int base) max))
                         (filter-steps (parse-int step))
                         (set))
                    (if-let [[from to] (split-range x)]
                      {(parse-int from) (parse-int to)}
                      (parse-int x)))))
           (into #{})
           (recex/normalize-set)))))

(def parse-m (int-parser (recex/unit->n :m)))

(def parse-h (int-parser (recex/unit->n :h)))

;; Confusing magic number, but works in the same way as using 60 for minutes
;; even though the minute slot only goes up to 59
(def parse-day (int-parser 32))

(defn parse-month-scalar [s]
  (or (some-> (parse-int s) (t/month))
      (t/month s)))

(defn parse-month [s]
  (->> (split-list s)
       (map (fn [x]
              (if-let [[base step] (split-step x)]
                (->> (if-let [[from to] (split-range base)]
                       (range (t/int (parse-month-scalar from))
                              (inc (t/int (parse-month-scalar to))))
                       (range (t/int (parse-month-scalar base)) (inc 12)))
                     (filter-steps (parse-int step))
                     (map t/month)
                     (set))
                (if-let [[from to] (split-range x)]
                  {(parse-month-scalar from) (parse-month-scalar to)}
                  (parse-month-scalar x)))))
       (into #{})
       (recex/normalize-set)))

(def int->day
  {0 (t/day-of-week "SUN")
   1 (t/day-of-week "MON")
   2 (t/day-of-week "TUE")
   3 (t/day-of-week "WED")
   4 (t/day-of-week "THUR")
   5 (t/day-of-week "FRI")
   6 (t/day-of-week "SAT")
   7 (t/day-of-week "SUN")})

(defn parse-dow-scalar [s]
  (if-some [i (parse-int s)]
    (if (zero? i) (t/day-of-week "SUN") (t/day-of-week i))
    (t/day-of-week s)))

(defn parse-dow [s]
  (->> (split-list s)
       (map (fn [x]
              (if-let [[base step] (split-step x)]
                (->> (if-let [[from to] (split-range base)]
                       (range (t/int (parse-dow-scalar from))
                              (inc (t/int (parse-dow-scalar to))))
                       (range (t/int (parse-dow-scalar base)) (inc 7)))
                     (filter-steps (parse-int step))
                     (map t/day-of-week)
                     (set))
                (if-let [[from to] (split-range x)]
                  {(parse-dow-scalar from) (parse-dow-scalar to)}
                  (parse-dow-scalar x)))))
       (into #{})
       (recex/normalize-set)))

(defn unwrap-simple [x]
  (if (and (set? x) (= 1 (count x))) (first x) x))

(defn time-expr [fields]
  (-> fields
      (select-keys [:m :h])
      (update :m parse-m)
      (update :m unwrap-simple)
      (update :h parse-h)
      (update :h unwrap-simple)))

(defn unwrap-simple-slots [recex]
  (if (set? recex)
    (into #{} (map unwrap-simple-slots) recex)
    (into [] (map unwrap-simple) recex)))

(defn cron->recex [cron]
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
        (unwrap-simple-slots))))

(comment



  (cron->recex "2-4  2-6/2 * 2 *")

  (cron->recex "2-4  2-6/2 2 2 *")

  (recex/valid? (cron->recex "2-4  2-6/2 2 2 2-4"))

  )
