(ns puihin.core
  (:require [datomic.api :as d]))

(def db-uri-base "datomic:mem://")

(defn scratch-conn
  "Create a connection to an anonymous, in-memory database."
  []
  (let [uri (str db-uri-base (d/squuid))]
    (println "Creating connection" uri)
    (d/delete-database uri)
    (d/create-database uri)
    (d/connect uri)))

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

(defn document-like-tree-data [root]
  [{:db/id root
    :node/text "root"
    :node/children
    [{:node/text "middle"
      :node/children
      [{:node/text "leaf1"}
       {:node/text "leaf2"}]}]}])

(def conn (scratch-conn))

(def temp-root-id (d/tempid :db.part/user))

; Schema installed with a transaction
(d/transact conn schema)

; Now let's put data in
(def tempids (:tempids @(d/transact conn (document-like-tree-data temp-root-id))))

; Get entity id for the root
(def root-entity-id (d/resolve-tempid (d/db conn) tempids temp-root-id))

(def whole-tree-query '[*])

(println "The whole tree as a map with query:" whole-tree-query)
(clojure.pprint/pprint (d/pull (d/db conn) whole-tree-query root-entity-id))

(def leafs-query '[{:node/children
                    [{:node/children
                      [:node/text]}]}])
(println "Only leafs with query:" leafs-query)
(clojure.pprint/pprint (d/pull (d/db conn) leafs-query root-entity-id))

;; http://www.jayway.com/2012/06/27/finding-out-who-changed-what-with-datomic/

;; need schema first
(d/transact conn [[:db/add root-entity-id :node/audit-user "John Doe"]])

(def audit-user-schema [{:db/id #db/id[:db.part/db]
                         :db/ident :node/audit-user
                         :db/valueType :db.type/string
                         :db/cardinality :db.cardinality/one
                         :db/fulltext true
                         :db/index true
                         :db.install/_attribute :db.part/db}])
(d/transact conn audit-user-schema)

; Now it should work
(d/transact conn [[:db/add root-entity-id :node/audit-user "John Doe"]])
