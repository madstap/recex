# recex

A recurrence expression, or a recex, is a domain specific language to
express an infinite series of recurring times.

## Project maturity

Still figuring out the api, breaking changes are likely.

## Rationale

In the [chime's readme](https://github.com/jarohen/chime#the-big-idea-behind-chime)
the author writes about how it tries to be the simplest possible scheduler.
It takes a sequence of times for executing a function, which makes it
infinitely flexible, it's just clojure code.

This works pretty well, but there are some tradeoffs.

Infinite sequences are pretty unwieldy, they can trip up your editor
if you try to print them. Modern clojure editors are smart about this
and only print a certain number of elements, but even then 10 or 100
elements at one key in a context map still really hurts readability
when you pretty print it to debug something.

Being code means that even if your scheduling needs are really simple, you still
need to implement them. If you want a schedule to be configurable, you need to
implement a function from data in a config file to a sequence of times.
This library aims to be that function. It tries be general enough to meet most
scheduling needs, while still being pretty readable in the 80% of simple cases.

Readability is helped by using tagged literals
(by default the ones bundled with [tick](https://juxt.pro/tick/docs/index.html)
, but that is [configurable](https://juxt.pro/tick/docs/index.html#_serialization)).

Programmability is helped by using edn data structures; it might be easier to
write code that generates a recex than writing the code that generates the
sequence of times itself.

## recex grammar

A recex is a vector containing at least one time component.

```clojure
;; Each day at 6 in the afternoon.
[#time/time "18:00"]

;; When not specified, the time zone defaults to UTC. The above is the same as:

[#time/time "18:00" #time/zone "UTC"]
```

Multiple times can be specified with a set.

```clojure
[#{#time/time "09:00"
   #time/time "17:00"} #time/zone "Europe/Oslo"]
```

Multiple recexes can themselves be combined by using a set.

```clojure
#{[#time/time "12:00" #time/zone "Europe/Oslo"]
  [#time/time "12:00" #time/zone "America/Sao_Paulo"]}
```

The time slot is the only required one, but you can also choose any
combination of months, days of week and days of month (in that order).

Month and day of week are java time types, while day of month is an integer.
Days of week can also be specified to be the nth day-of-week in that month,
by using a vector of [nth day] instead of just day.

Both nth day of week and day of month can be negative numbers, in which
`[-1 #time/day-of-week "FRIDAY"]` means the last friday of the month,
and -1 means the last day of the month.

Just like times of day, you can `OR` them together by putting them in a set.

Some examples will make this clearer:

```clojure
;; https://en.wikipedia.org/wiki/Triple_witching_hour
;; 15:00 every third friday in March, June, September and December New York time.
[#{#time/month "MARCH"     #time/month "JUNE"
   #time/month "SEPTEMBER" #time/month "DECEMBER"}
 [3 #time/day-of-week "FRIDAY"]
 #time/time "15:00"
 #time/zone "America/New_York"]

;; Midnight every friday the 13th
[#time/day-of-week "FRIDAY" 13 #time/time "00:00"]

;; The last monday of each month at noon.
[[-1 #time/day-of-week "MONDAY"] #time/time "12:00"]

;; The second to last day of each month that is also a monday, at noon.
[#time/day-of-week "MONDAY" -2 #time/time "12:00"]
```

## Clojure API

Require the namespace:

```clojure
(ns my.app
  (:require
   [madstap.recex :as recex]
   [tick.core :as t]))
```

The `recex/time` function generates an infinite sequence of times from a recex.
It takes a time `now` which can be either an instant, zoned-date-time
or offset-date-time, (ie anything that represents an instant in time) and a recex.

`now` can be omitted, in which case it will use `t/now`, but it's
not recommended to use the implicit now except for experimenting at the repl
because passing `now` down from higher in the call stack makes testing easier.

(Remember to `take` when experimenting at the repl, it's an infinite sequence)

```Clojure
(take 2 (recex/times (t/now) [#time/time "00:00"]))
;; => (#time/zoned-date-time "2019-09-06T00:00Z[UTC]"
;;     #time/zoned-date-time "2019-09-07T00:00Z[UTC]")

(take 2 (recex/times [#time/time "00:00"]))
;; => (#time/zoned-date-time "2019-09-06T00:00Z[UTC]"
;;     #time/zoned-date-time "2019-09-07T00:00Z[UTC]")

```

## Prior art

### cron

### schyntax

### iCalendar recurrence rules
