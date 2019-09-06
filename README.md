# recex

A recurrence expression, or a recex, is a domain specific language to
express an infinite series of recurring times.

## Project maturity

Still figuring out the api, breaking changes are likely.

## Usage

I haven't deployed to clojars yet, but you can depend on the library with `deps.edn`.

```clojure
madstap/recex {:git/url "https://github.com/madstap/recex.git"
               :sha "4133259c26620b8f13757ac2bb3f866230def462"}
```

## Rationale

In the [chime readme](https://github.com/jarohen/chime#the-big-idea-behind-chime)
the author writes about how it tries to be the simplest possible scheduler.
It takes a sequence of times for executing a function, which makes it
infinitely flexible, it's just clojure code. You will never hit a wall where
it's very hard, or impossible, to express what you need.

This works pretty well, but there are some tradeoffs.

In general, a dsl trades flexibility for leverage. You get to express
yourself on the level of the domain instead of in terms of a general
purpose programming language.

A more specific issue I've run into with using infinite sequences as
schedules is that they are pretty unwieldy, they can trip up your
editor if you try to print them. Modern clojure editors are smart
about this and only print a certain number of elements, but even then
10 or 100 elements at one key in a context map still really hurts
readability when you pretty print it to debug something.

Being code means that even if your scheduling needs are really simple,
you still need to implement them. If you want a schedule to be
configurable, you need to implement a function from data in a config
file to a sequence of times. Sticking a schedule in a config file was
the original use-case for this library.

This is not to dunk on chime, it works great, and in fact you can
pretty trivially use it with the sequence of times that this library
gives you.  You just need to transform the times to joda-times.
(This is lossy as it throws away zone/offset information.)

```clojure
(require '[clj-time.coerce :as ct.coerce])

(defn zdt-or-odt->joda-time [zdt-or-odt]
  (-> zdt-or-odt .toInstant java.util.Date/from ct.coerce/from-date))
```

Since we are using clojure, we prefer a dsl defined in
terms of data instead of one defined in terms of text. While using
just the dsl gives you quite a bit, you might want to also write code
that generates a recex.

Readability is helped by using tagged literals (by default the ones
bundled with [tick](https://juxt.pro/tick/docs/index.html)
([time-literals](https://github.com/henryw374/time-literals)), but that
is [configurable](https://juxt.pro/tick/docs/index.html#_serialization)).
This hopefully makes it pretty obvious what is happening, even if you
don't know the dsl well.


## recex grammar

A recex a vector with slots: `[month day-of-week day-of-month time time-zone]`.

The different slots are `AND`ed together, while sets in a single slot means `OR`.

```clojure
;; Each day at 6 in the afternoon.
[#time/time "18:00"]

;; When not specified, the time zone defaults to UTC. The above is the same as:
[#time/time "18:00" #time/zone "UTC"]

;; When not specified, the time defaults to midnight. These are all the same:
[]
[#time/time "00:00"]
[#time/time "00:00" #time/zone "UTC"]
[#time/zone "UTC"]
```

Multiple times can be specified with a set. This also goes for all the other slots.

```clojure
[#{#time/time "09:00"
   #time/time "17:00"} #time/zone "Europe/Oslo"]
```

Multiple recexes can themselves be combined by using a set.

```clojure
#{[#time/time "12:00" #time/zone "Europe/Oslo"]
  [#time/time "14:00" #time/zone "America/Sao_Paulo"]}
```

Month and day of week are java time types, while day of month is an integer.
Days of week can also be specified to be the nth day-of-week in that month,
by using a vector of `[nth day]` instead of just day.

Both nth day of week and day of month can be negative numbers, in which
`[-1 #time/day-of-week "FRIDAY"]` means the last friday of the month,
and `-1` means the last day of the month.

Some examples will hopefully make this clearer:

```clojure
;; https://en.wikipedia.org/wiki/Triple_witching_hour
;; 15:00 New York time every third friday in March, June, September and December.
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
   [tick.alpha.api :as t]))
```

The `recex/time` function generates an infinite sequence of times from a recex.
The times are either zoned-date-time or offset-date-time depending on whether a
time zone or an offset was specified.

It takes a time `now` which can be either an instant, zoned-date-time
or offset-date-time, (ie anything that represents an instant in time) and a recex.

`now` can be omitted, in which case it will use `t/now`, but it's
not recommended to use the implicit now except for experimenting at the repl.
Omitting the `now` argument makes the function impure, and thus trickier to test.

(Remember to `take` when experimenting at the repl, it is an infinite sequence.)

```Clojure
(take 2 (recex/times (t/now) [#time/time "00:00"]))
;; => (#time/zoned-date-time "2019-09-06T00:00Z[UTC]"
;;     #time/zoned-date-time "2019-09-07T00:00Z[UTC]")

(take 2 (recex/times [#time/time "00:00"]))
;; => (#time/zoned-date-time "2019-09-06T00:00Z[UTC]"
;;     #time/zoned-date-time "2019-09-07T00:00Z[UTC]")
```



## Caveats/warnings

### Impossible combinations

Specifying an impossible recex is not considered valid, but currently
this is not checked. For example:

```clojure
;; The last friday of the month that is also the 1st day of the month.
[[-1 #time/day-of-week "FRIDAY"] 1 #time/time "12:00"]
```

This is an impossible combination, and the current behavior is an infinite loop.
So don't accept user defined recexes in api endpoints without at least a timeout,
lest you open yourself up to denial of service attacks.

Doing some math to validate whether a recex is impossible should be doable,
but may be non-trivial. PRs welcome.

### Unexpressable concepts

Currently the dsl specifies things like "every last monday of the
month at noon in some time zone" and not things like "every 5 minutes
for an hour every friday in the system time zone", which is easy to
specify in cron. Stuff like "every x time units starting at y" is not
necessarily out of scope, I just haven't found a good syntax for it,
nor have I personally had that use case yet. Suggestions are very welcome.

As an aside, even without a way to express those things, I think it
would still be possible to write a `cron->recex` function and just
expand repetitions into a bunch of time objects.


## Prior art

### cron

cron is the de-facto standard time dsl. It is text-based, and not very
readable (YMMV). Time zones are not part of the expression itself, it
uses the system time zone or you can choose the time zone with config files
or env vars, which is quite fiddly.
However, like discussed above it can express repeating times of the
form "every x minutes" which is not expressable without repetition in
recexes.

### [schyntax](https://github.com/schyntax/schyntax)

A time dsl with implementations in js, .NET and go. It has pretty
similar features to recex, but also easily expresses "every n minutes"
type repetitions and has negations, which I'd be interested to add to
recexes.  It works only in UTC, however, which makes it impossible to
express things like every day at noon in some time zone with dst.

### [iCalendar recurrence rules](https://www.kanzaki.com/docs/ical/rrule.html)

[RFC 2445](https://www.ietf.org/rfc/rfc2445.txt)

Very complete language to express recurrences, has features like up to some time,
every x units for n ocurrences and every n years.

The `tick.ical` namespace deals with these, but I don't know how complete it is.
There's also an [ical4j](https://github.com/ical4j).
