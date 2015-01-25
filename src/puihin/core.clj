(ns puihin.core
  (:require [datomic.api :as d]
            [clojure.pprint :refer [pprint]]))

(def pp pprint)

(defn make-conn
  "Create a connection to existing database, or to an anonymous in-memory database"
  ([]
   (make-conn (str "datomic:mem://" (d/squuid))))
  ([uri]
   (println "Creating connection" uri)
   (d/delete-database uri)
   (d/create-database uri)
   (d/connect uri)))

(def possu "datomic:sql://puihin?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic")

;; Connecting people
(def conn (make-conn possu))

;; Schema as nested data
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

;; Schema installed with a transaction
(d/transact conn schema)

;; Temporary id can be used to refer to an entity before a transction occurs
(def temp-root-id (d/tempid :db.part/user))

;; Let's have some data
(defn document-like-tree-data [root]
  [{:db/id root
    :node/text "root"
    :node/children
    [{:node/text "middle"
      :node/children
      [{:node/text "leaf1"}
       {:node/text "leaf2"}]}]}])

;; Now let's put data into datomic
(def tx @(d/transact conn (document-like-tree-data temp-root-id)))

;; Get entity id for the root
(def root-entity-id (d/resolve-tempid (d/db conn) (:tempids tx) temp-root-id))

(def whole-tree-query '[*])

;; Whole tree, with Pull API
;; http://blog.datomic.com/2014/10/datomic-pull.html
(pp (d/pull (d/db conn) whole-tree-query root-entity-id))

;; Find text of all nodes with children, Entity API, with Unification (~join)
(pp (d/q '[:find ?text
           :where
           [?e :node/text ?text]
           [?e :node/children]]
         (d/db conn)))

;; Only leafs (no children)
(pp (d/q '[:find ?text
           :where
           [?e :node/text ?text]
           (not [?e :node/children])]
         (d/db conn)))

;; Pull API: Only leafs, kind of, well, this is navigation :)
(pp (d/pull (d/db conn) '[{:node/children
                           [{:node/children
                             [:node/text]}]}] root-entity-id))

;; Find text of all nodes, as set
(pp (d/q '[:find ?text
           :where
           [_ :node/text ?text]]
         (d/db conn)))







; Let's make changes
(d/transact conn [[:db/add root-entity-id :node/text "Root of all evil"]])

;; Make leafs contain same text
(def evil (map (fn [e]
                 {:db/id (first e)
                  :node/text "evil"})
               (d/q '[:find ?e
                      :where
                      [?e :node/text ?text]
                      (not [?e :node/children])]
                    (d/db conn))))
(pp evil)
(pp (d/transact conn evil))

;; Find text of all nodes, including duplicates
(pp (map first (d/q '[:find ?text ?e
                      :where
                      [?e :node/text ?text]]
                    (d/db conn))))







;; http://www.jayway.com/2012/06/27/finding-out-who-changed-what-with-datomic/

;; Whole tree again
(pp (d/pull (d/db conn) whole-tree-query root-entity-id))

(def first-txid (:tx (first (:tx-data tx))))

;; DB as of dawn of time
(def db-as-of (d/as-of (d/db conn) first-txid))
(pp (d/pull db-as-of '[*] root-entity-id))

;; Stuff that happened since dawn of time
(def db-since (d/since (d/db conn) first-txid))

(pp (map #(d/touch (d/entity db-since (:e %)))
         (d/datoms db-since :eavt)))

;; Do some funny stuff with root-entity-id
;; (pp (d/pull db-since '[*] root-entity-id))









;; * Console demo *










;; * Changing schema *

;; Let's add audit user
(def data-with-audit [{:db/id root-entity-id :node/audit-user "John Doe"}
                      {:db/id root-entity-id :node/text "Just a root"}])

;; Need schema first
(d/transact conn data-with-audit)

(def audit-user-schema [{:db/id #db/id[:db.part/db]
                         :db/ident :node/audit-user
                         :db/valueType :db.type/string
                         :db/cardinality :db.cardinality/one
                         :db/fulltext true
                         :db/index true
                         :db.install/_attribute :db.part/db}])
(pp (d/transact conn audit-user-schema))

; Now it should work
(pp (d/transact conn data-with-audit))
(pp (d/pull (d/db conn) '[*] root-entity-id))

;; Let's monitor transactions
(def tx-reports (d/tx-report-queue conn))
(pp (.peek tx-reports))

;; What if
(def maybe-tx (d/with (d/db conn) [{:db/id root-entity-id :node/text "moi"}]))
(pp (d/q `[:find ?text
           :where
           [~root-entity-id :node/text ?text]]
         (:db-after maybe-tx)))

;; With all of history at our hands
(def db-all-history (d/history (d/db conn)))
(pp (d/q '[:find ?text :where [_ :node/text ?text]] db-all-history))
