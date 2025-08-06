.PHONY: test deploy

test:
	bin/kaocha --no-watch

recex.jar: src/**/*
	clojure -M:jar

pom.xml:
	clojure -Spom

deploy: pom.xml test recex.jar
	clojure -M:deploy
