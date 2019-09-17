.PHONY: test deploy

test:
	bin/kaocha --no-watch

recex.jar: src/**/*
	clojure -A:jar

pom.xml:
	clojure -Spom

deploy: pom.xml test recex.jar
	clojure -A:deploy
