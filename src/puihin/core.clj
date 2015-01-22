(ns puihin.core
  (:require [datomic.api :as d])
  (:gen-class))

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

(defn -main [& args]
  (let [conn (scratch-conn)
        temp-root-id (d/tempid :db.part/user)]
    (d/transact conn schema)
    (let [tempids (:tempids @(d/transact conn (document-like-tree-data temp-root-id)))
          root-entity-id (d/resolve-tempid (d/db conn) tempids temp-root-id)]
      (println "The whole tree as a map with query:" '[*])
      (clojure.pprint/pprint (d/pull (d/db conn) '[*] root-entity-id))
      (println "Only leafs with query:" '[{:node/children
                                           [{:node/children
                                             [:node/text]}]}])
      (clojure.pprint/pprint (d/pull (d/db conn) '[{:node/children
                                                    [{:node/children
                                                      [:node/text]}]}] root-entity-id)))))
