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

(defn parse-month [s]
  (or (and (parse-int s) (t/month (parse-int s)))
      (t/month s)))

(defn parse-day [s]
  )

(defn filter-steps [step xs]
  (->> (medley/indexed xs)
       (keep (fn [[idx x]]
               (when (zero? (mod idx step))
                 x)))))

(def wildcard? #{"*"})

(defn time-parser [unit]
  (fn [s]
    (if (wildcard? s)
      {0 (dec (recex/unit->n unit))}
      (->> (split-list s)
           (map (fn [x]
                  (if-let [[base step] (split-step x)]
                    (->> (if-let [[from to] (split-range base)]
                           (range (parse-int from) (inc (parse-int to)))
                           (range (parse-int base) (recex/unit->n unit)))
                         (filter-steps (parse-int step))
                         (set))
                    (if-let [[from to] (split-range x)]
                      {(parse-int from) (parse-int to)}
                      (parse-int x)))))
           (into #{})
           (recex/normalize-set)))))

(def parse-m (time-parser :m))

(def parse-h (time-parser :h))

(defn time-expr [fields]
  (-> fields
      (select-keys [:m :h])
      (update :m parse-m)
      (update :h parse-h)))

(defn cron->recex [cron]
  (let [fields (split-fields cron)
        time (time-expr fields)]
    [time]))

(comment

  (cron->recex "2-3,6-7       2-10/2       *       *       *")

  (time-expr (split-fields "2-3,6-7       2-10/2       *       2       *"))

  (split-range "2-10")

  (split-range "2-3")

  (recex/unit->n :m)

  (parse-m "*")

  (parse-m "1,2-4,50")

  (parse-m "2-10/2")

  (def s "2-10/2")

  (-> (split-list s))

  (split-step s)

  (filter-steps (range (inc 10)) 4)

  (split-range "2-3")

  (split-range "2")

  (split-list "2,3,2")

  (map parse-int (split-list "2,3/4,4-5"))

  )
