# clj-joq-client

Clojure implementation of the joq client protocol. See https://github.com/nikopol/joq

## Usage

```clojure
  (with-open-joq-client {:host "localhost" :port 1970}
    (joq-cmd :status))

  ;; or with a persistent connection
  (def client (create-joq-client))
  (with-joq-client client
    (joq-cmd :add "code while(1){ print \"bazinga\n\"; sleep 10; } name=bazinga delay=30"))
  (close-joq-client client)
```

## License

Copyright (C) 2012 Linkfluence

Distributed under the Eclipse Public License, the same as Clojure.
