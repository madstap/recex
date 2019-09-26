(ns madstap.recex.util)

(defn normalize-set [x]
  (if (set? x)
    (into #{} (mapcat normalize-set) x)
    #{x}))

(def unit->n
  {:h 24
   :m 60
   :s 60})
