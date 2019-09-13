(ns madstap.recex-test
  (:require
   [clojure.test :refer [deftest testing is are run-tests test-var]]
   [cljc.java-time.zoned-date-time :as zoned-date-time]
   [tick.core :as t]
   [madstap.recex :as rec]))

(defn yr [y]
  (t/instant (str y "-01-01T00:00:00Z")))

(deftest empty-recex
  (is (= [#time/zoned-date-time "2019-09-04T00:00Z[UTC]"
          #time/zoned-date-time "2019-09-05T00:00Z[UTC]"
          #time/zoned-date-time "2019-09-06T00:00Z[UTC]"]
         (take 3 (rec/times [] #time/instant "2019-09-03T22:00:00Z")))))

(deftest simple-times-of-day-test
  (is (= [#time/zoned-date-time "2019-09-04T01:30+01:00"
          #time/zoned-date-time "2019-09-05T01:30+01:00"
          #time/zoned-date-time "2019-09-06T01:30+01:00"]
         (take 3 (rec/times [#time/time "01:30" #time/zone "+01:00"]
                            #time/instant "2019-09-03T22:00:00Z"))))

  (is (= [#time/zoned-date-time "2019-09-04T01:30-03:00[America/Sao_Paulo]"
          #time/zoned-date-time "2019-09-05T01:30-03:00[America/Sao_Paulo]"
          #time/zoned-date-time "2019-09-06T01:30-03:00[America/Sao_Paulo]"]
         (take 3 (rec/times [#time/time "01:30" #time/zone "America/Sao_Paulo"]
                            #time/instant "2019-09-03T22:00:00Z"))))

  (is (= #time/zoned-date-time "2019-09-05T01:00+02:00[Europe/Oslo]"
         (first (rec/times
                 [#time/time "01:00" #time/zone "Europe/Oslo"]
                 ;; The same instant as:
                 ;; #time/zoned-date-time "2019-09-04T03:00+02:00[Europe/Oslo]"
                 #time/instant "2019-09-04T01:00:00Z"))))

  (is (= [#time/zoned-date-time "2019-09-04T01:30-03:00[America/Sao_Paulo]"
          #time/zoned-date-time "2019-09-04T02:30-03:00[America/Sao_Paulo]"
          #time/zoned-date-time "2019-09-05T01:30-03:00[America/Sao_Paulo]"]
         (take 3 (rec/times [#{#time/time "01:30"
                               #time/time "02:30"}
                             #time/zone "America/Sao_Paulo"]
                            #time/instant "2019-09-03T22:00:00Z")))))

(deftest multiple-time-zones
  (is (= [#time/zoned-date-time "2019-09-04T12:00+02:00[Europe/Oslo]"
          #time/zoned-date-time "2019-09-04T12:00-03:00[America/Sao_Paulo]"
          #time/zoned-date-time "2019-09-05T12:00+02:00[Europe/Oslo]"]
         (take 3 (rec/times [#time/time "12:00" #{#time/zone "Europe/Oslo"
                                                  #time/zone "America/Sao_Paulo"}]
                            #time/instant "2019-09-03T22:00:00Z")))))

(deftest combining-recexes
  (is (= [#time/zoned-date-time "2019-09-04T12:00+02:00[Europe/Oslo]"
          #time/zoned-date-time "2019-09-04T14:00-03:00[America/Sao_Paulo]"
          #time/zoned-date-time "2019-09-05T12:00+02:00[Europe/Oslo]"]
         (take 3 (rec/times #{[#time/time "12:00" #time/zone "Europe/Oslo"]
                              [#time/time "14:00" #time/zone "America/Sao_Paulo"]}
                            #time/instant "2019-09-03T22:00:00Z")))))

(deftest friday-13th
  (is (= [#time/zoned-date-time "2019-09-13T00:00Z[UTC]"
          #time/zoned-date-time "2019-12-13T00:00Z[UTC]"
          #time/zoned-date-time "2020-03-13T00:00Z[UTC]"
          #time/zoned-date-time "2020-11-13T00:00Z[UTC]"]
         (take 4
               (rec/times [#time/day-of-week "FRIDAY"
                           13
                           #time/time "00:00"]
                          #time/instant "2019-01-01T00:00:00Z")))))

(deftest negative-days-of-week-and-month
  (is (= [#time/zoned-date-time "2019-09-30T12:00Z[UTC]"
          #time/zoned-date-time "2019-10-28T12:00Z[UTC]"]
         (take 2 (rec/times
                  [[-1 #time/day-of-week "MONDAY"] #time/time "12:00"]
                  #time/instant "2019-09-03T22:00:00Z"))))

  (is (= [#time/zoned-date-time "2019-12-30T12:00Z[UTC]"
          #time/zoned-date-time "2020-03-30T12:00Z[UTC]"]
         (take 2 (rec/times [#time/day-of-week "MONDAY" -2 #time/time "12:00"]
                            #time/instant "2019-09-03T22:00:00Z")))))

(deftest triple-witching-days
  (is (= [#time/zoned-date-time "2019-03-15T15:00-04:00[America/New_York]"
          #time/zoned-date-time "2019-06-21T15:00-04:00[America/New_York]"
          #time/zoned-date-time "2019-09-20T15:00-04:00[America/New_York]"
          #time/zoned-date-time "2019-12-20T15:00-05:00[America/New_York]"]
         (take 4
               (rec/times
                [#{#time/month "MARCH"     #time/month "JUNE"
                   #time/month "SEPTEMBER" #time/month "DECEMBER"}
                 [3 #time/day-of-week "FRIDAY"]
                 #time/time "15:00"
                 #time/zone "America/New_York"]
                #time/zoned-date-time "2019-01-01T00:00-05:00[America/New_York]")))))

(deftest terse-syntax
  (is (= [#time/zoned-date-time "2019-09-13T00:00+03:00[Europe/Helsinki]"
          #time/zoned-date-time "2019-12-13T00:00+02:00[Europe/Helsinki]"]
         (take 2 (rec/times [#{:september :december}
                             :friday 13
                             "00:00"
                             "Europe/Helsinki"]
                            (yr 2019)))))
  (is (= #time/zoned-date-time "2023-10-13T00:00Z[UTC]"
         (first (rec/times [:october :friday 13] (yr 2019))))))

(deftest normalize-set-test
  (is (= #{0 1 2 3 4} (rec/normalize-set #{0 #{1} #{2} #{#{3} #{#{4}}}})))
  (is (= #{1} (rec/normalize-set 1))))

(deftest filters
  (is (true?
       ((rec/month-filter #time/month "SEPTEMBER")
        #time/zoned-date-time "2019-09-04T14:48:55.382-03:00[America/Sao_Paulo]")))
  (is (true?
       ((rec/month-filter #time/month "SEPTEMBER")
        #time/date "2019-09-04")))
  (is (false?
       ((rec/month-filter #time/month "JANUARY")
        #time/zoned-date-time "2019-09-04T14:48:55.382-03:00[America/Sao_Paulo]")))
  (is (true?
       ((rec/day-of-week-filter #time/day-of-week "MONDAY")
        #time/zoned-date-time "2019-09-30T14:39:39.983-03:00[America/Sao_Paulo]")))
  (is (true?
       ((rec/day-of-week-filter #time/day-of-week "MONDAY")
        #time/date "2019-09-30")))
  (is (false?
       ((rec/day-of-week-filter #time/day-of-week "FRIDAY")
        #time/zoned-date-time "2019-09-30T14:39:39.983-03:00[America/Sao_Paulo]")))
  (is (true?
       ((rec/nth-day-of-week-filter 5 #time/day-of-week "MONDAY")
        #time/zoned-date-time "2019-09-30T14:39:39.983-03:00[America/Sao_Paulo]")))
  (is (false?
       ((rec/nth-day-of-week-filter 4 #time/day-of-week "MONDAY")
        #time/zoned-date-time "2019-09-30T14:39:39.983-03:00[America/Sao_Paulo]")))
  (is (false?
       ((rec/nth-day-of-week-filter 4 #time/day-of-week "MONDAY")
        #time/date "2019-09-30")))
  (is (false?
       ((rec/nth-day-of-week-filter 5 #time/day-of-week "FRIDAY")
        #time/zoned-date-time "2019-09-30T14:39:39.983-03:00[America/Sao_Paulo]"))))

(deftest dst-edge-cases
  (testing "Overlap"
    (is (= [#time/zoned-date-time "2015-11-01T01:30-07:00[America/Los_Angeles]"
            #time/zoned-date-time "2016-11-01T01:30-07:00[America/Los_Angeles]"]
           (take 2 (rec/times [:nov 1 "01:30" "America/Los_Angeles"] (yr 2015)))
           (take 2 (rec/times [:nov 1 "01:30" "America/Los_Angeles"
                               {:dst/overlap :first}]
                              (yr 2015)))))

    (is (= [(zoned-date-time/with-earlier-offset-at-overlap
              #time/zoned-date-time "2015-11-01T01:30-07:00[America/Los_Angeles]")
            (zoned-date-time/with-later-offset-at-overlap
              #time/zoned-date-time "2015-11-01T01:30-08:00[America/Los_Angeles]")]
           (take 2 (rec/times [:nov 1 "01:30" "America/Los_Angeles"
                               {:dst/overlap :both}]
                              (yr 2015)))))

    (is (= [(zoned-date-time/with-later-offset-at-overlap
              #time/zoned-date-time "2015-11-01T01:30-08:00[America/Los_Angeles]")
            #time/zoned-date-time "2016-11-01T01:30-07:00[America/Los_Angeles]"]
           (take 2 (rec/times [:nov 1 "01:30" "America/Los_Angeles"
                               {:dst/overlap :second}]
                              (yr 2015))))))

  (testing "Gap"
    (is (= [#time/zoned-date-time "2019-03-31T03:30+02:00[Europe/Oslo]"
            #time/zoned-date-time "2020-03-31T02:30+02:00[Europe/Oslo]"]
           (take 2 (rec/times [:mar 31 "02:30" "Europe/Oslo"] (yr 2019)))
           (take 2 (rec/times [:mar 31 "02:30" "Europe/Oslo"
                               {:dst/gap :include}]
                              (yr 2019)))))

    (is (= (repeat 2 #time/zoned-date-time "2019-03-31T03:30+02:00[Europe/Oslo]")
           (take 2 (rec/times [:mar 31 #{"02:30" "03:30"} "Europe/Oslo"
                               {:dst/gap :include}]
                              (yr 2019)))))

    (is (= [#time/zoned-date-time "2019-03-31T03:30+02:00[Europe/Oslo]"
            #time/zoned-date-time "2020-03-31T02:30+02:00[Europe/Oslo]"]
           (take 2 (rec/times [:mar 31 #{"02:30" "03:30"} "Europe/Oslo"
                               {:dst/gap :skip}]
                              (yr 2019)))))))

(deftest expand-times-test
  (is (= #{#time/time "00:00" #time/time "02:02" #time/time "03:02" #time/time "04:02"}
         (rec/expand-times #{#time/time "00:00" {:m 2 :h {2 4}}})))
  (is (= (* 60 24) (count (rec/expand-times #{{:s 5}}))))
  (is (= (* 2 60 24) (count (rec/expand-times #{{:s #{0 30}}}))))
  (is (= 24 (count (rec/expand-times #{{:m 5}}))))
  (is (= (* 3 120) (count (rec/expand-time-expr {:h {10 12} :s #{0 30}})))))

(deftest time-exprs
  (is (= [#time/zoned-date-time "2019-01-05T00:00:00Z[UTC]"
          #time/zoned-date-time "2019-01-05T00:00:15Z[UTC]"
          #time/zoned-date-time "2019-01-05T00:00:30Z[UTC]"
          #time/zoned-date-time "2019-01-05T00:00:45Z[UTC]"
          #time/zoned-date-time "2019-01-05T00:01:15Z[UTC]"]
         (take 5 (rec/times
                  [:saturday #{"00:00" {:s #{15 30 45}}}]
                  (yr 2019))))))

(def weekdays
  (set (map t/day-of-week (range 1 (inc 5)))))

(def first-qtr
  #{:january :february :march})

(deftest ranges
  (is (= (take 6 (rec/times [weekdays] (yr 2019)))
         (take 6 (rec/times [{:monday :friday}] (yr 2019)))))
  (is (= (take 4 (rec/times [first-qtr 15] (yr 2019)))
         (take 4 (rec/times [{:january :march} 15] (yr 2019)))))
  (is (= (take 10 (rec/times [{1 15}] (yr 2019)))
         (take 10 (rec/times [(set (range 1 (inc 15)))] (yr 2019))))))

(defn inc-hour [t]
  (t/+ t (t/new-duration 1 :hours)))

(comment
  ;;;;;;;;;;;;;;;;;;
  ;;; Going back

  ;; When we go back an hour, there's an hour that repeats.
  ;; In a naive implementation something scheduled at 01:30
  ;; would happen twice that day.
  (def back
    (t/in #time/date-time "2015-11-01T01:00" #time/zone "America/Los_Angeles"))

  back            ;=> #time/zoned-date-time "2015-11-01T01:00-07:00[America/Los_Angeles]"
  (inc-hour back) ;=> #time/zoned-date-time "2015-11-01T01:00-08:00[America/Los_Angeles]"

  ;; With the current behavior it happens only once.
  ;; This is probably what you'd want, but people have strange requirements,
  ;; so maybe it's really important that every one thirty at night is counted,
  ;; even if they're only one hour apart.
  (take 2 (rec/times (yr 2015) [:nov 1 "01:30" "America/Los_Angeles"]))
  ;; =>
  ;; (#time/zoned-date-time "2015-11-01T01:30-07:00[America/Los_Angeles]"
  ;;  #time/zoned-date-time "2016-11-01T01:30-07:00[America/Los_Angeles]")

  ;; Another possible point of confusion is that the _first_
  ;; one thirty at night is included (offset -7),
  ;; but maybe you want the second one instead (offset -8).


  ;;;;;;;;;;;;;;;;;;
  ;; Going forward

  ;; When we go forward an hour, there's an hour that is lost.
  ;; In a naive implementation something scheduled at 02:30
  ;; wouldn't happen that day.
  (def fwd
    (t/in #time/date-time "2019-03-31T01:00" #time/zone "Europe/Oslo"))

  fwd            ;=> #time/zoned-date-time "2019-03-31T01:00+01:00[Europe/Oslo]"
  (inc-hour fwd) ;=> #time/zoned-date-time "2019-03-31T03:00+02:00[Europe/Oslo]"

  ;; With the current behavior it happens, but at 03:30.
  ;; If you're scheduling something once a day, that's probably what you'd want.
  (take 2 (rec/times [:mar 31 "02:30" "Europe/Oslo"] (yr 2019)))
  ;; =>
  ;; (#time/zoned-date-time "2019-03-31T03:30+02:00[Europe/Oslo]"
  ;;  #time/zoned-date-time "2020-03-31T03:30+02:00[Europe/Oslo]")

  ;; However, if you've scheduled something at both 02:30 and 03:30 then you're
  ;; gonna have two events happening at the same time, which will be confusing.
  (take 2 (rec/times (yr 2019) [:mar 31 #{"02:30" "03:30"} "Europe/Oslo"]))
  ;; =>
  ;; (#time/zoned-date-time "2019-03-31T03:30+02:00[Europe/Oslo]"
  ;;  #time/zoned-date-time "2019-03-31T03:30+02:00[Europe/Oslo]")

  ;; Possible syntax for choosing which behavior you want.
  ;; This would be equal to the naive implementation.
  [:mar 31 #{"02:30" "03:30"} "Europe/Oslo" {:dst/backward :both
                                             :dst/forward :skip}]

  ;; All(?) possible values.
  {:dst/backward #{:first :second :both}
   :dst/forward #{:skip :include}}

  ;; Defaults:
  {:dst/backward :first
   :dst/forward :include}

  ;; The namespace dst should just be a shorthand for :madstap.recex/dst
  ;; so the spec doesn't conflict with any user defined ones.

  (run-tests)
  )
