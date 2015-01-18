# puihin

## Demo of Datomic as document like storage:

Let's have a tree/document -like structure with nodes having text
attribute and child nodes (imagine a table of contents).

```clojure
(def schema
  [{:db/id #db/id[:db.part/db]
    :db/ident :node/text
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/fulltext true
    :db/index true
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :node/children
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/isComponent true
    :db.install/_attribute :db.part/db}])
```

Datomic allows to write a whole 'document' at once with [nested maps](http://docs.datomic.com/transactions.html#sec-3-4):

```clojure
(defn document-like-tree-data [root]
  [{:db/id root
    :node/text "root"
    :node/children
    [{:node/text "middle"
      :node/children
      [{:node/text "leaf1"}
       {:node/text "leaf2"}]}]}])
```

[Pull API](http://blog.datomic.com/2014/10/datomic-pull.html) allows
to get it back as a nested map.

```clojure
(d/pull (d/db conn) '[*] root-entity-id)
{:db/id 17592186045418,
 :node/text "root",
 :node/children
 [{:db/id 17592186045419,
   :node/text "middle",
   :node/children
   [{:db/id 17592186045420, :node/text "leaf1"}
    {:db/id 17592186045421, :node/text "leaf2"}]}]}
```

Neat!

## License

Copyright © 2015 Kimmo Koskinen

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
