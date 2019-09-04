# recex (Recurrence Expressions)

A recurrence expression, or a recex, is a domain specific language to
express a series of recurring times. It is defined in terms of edn
data structures instead of text.

## Project maturity

Still figuring out the api, breaking changes are likely.

## recex grammar

A recex is a vector containing at least one time component.

```clojure
[#time/time "18:00"]

;; When not specified, the time zone defaults to UTC. The above is the same as:

[#time/time "18:00" #time/zone "UTC"]
```

Multiple times can be specified with a set.

```clojure
[#{#time/time "09:00" #time/time "17:00"} #time/zone "Europe/Oslo"]
```

## Clojure API


## Prior art

### cron

### schyntax

### iCalendar recurrence rules
