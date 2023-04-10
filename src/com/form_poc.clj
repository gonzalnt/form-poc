(ns com.form-poc
  (:require [com.biffweb :as biff]
            [com.form-poc.email :as email]
            [com.form-poc.feat.items :as items]
            [com.form-poc.feat.home :as home]
            [com.form-poc.feat.worker :as worker]
            [com.form-poc.schema :as schema]
            [clojure.test :as test]
            [clojure.tools.logging :as log]
            [malli.core :as malc]
            [malli.registry :as malr]
            [nrepl.cmdline :as nrepl-cmd]))

(def features
  [items/features
   (biff/authentication-plugin {:biff.auth/app-path "/items"})
   home/features
   schema/features
   worker/features])

(def routes [["" {:middleware [biff/wrap-site-defaults]}
              (keep :routes features)]
             ["" {:middleware [biff/wrap-api-defaults]}
              (keep :api-routes features)]])

(def handler (-> (biff/reitit-handler {:routes routes})
                 biff/wrap-base-defaults))

(def static-pages (apply biff/safe-merge (map :static features)))

(defn generate-assets! [sys]
  (biff/export-rum static-pages "target/resources/public")
  (biff/delete-old-files {:dir "target/resources/public"
                          :exts [".html"]}))

(defn on-save [sys]
  (biff/add-libs)
  (biff/eval-files! sys)
  (generate-assets! sys)
  (test/run-all-tests #"com.form-poc.test.*"))

(def malli-opts
  {:registry (malr/composite-registry
              malc/default-registry
              (apply biff/safe-merge
                     (keep :schema features)))})

(def components
  [biff/use-config
   biff/use-secrets
   biff/use-xt
   biff/use-queues
   biff/use-tx-listener
   biff/use-wrap-ctx
   biff/use-jetty
   biff/use-chime
   (biff/use-when
    :com.form-poc/enable-beholder
    biff/use-beholder)])

(defn start []
  (let [ctx (biff/start-system
             {:com.form-poc/chat-clients (atom #{})
              :biff/send-email #'email/send-email
              :biff/features #'features
              :biff/after-refresh `start
              :biff/handler #'handler
              :biff/malli-opts #'malli-opts
              :biff.beholder/on-save #'on-save
              :biff.xtdb/tx-fns biff/tx-fns
              :biff/components components})]
    (generate-assets! ctx)
    (log/info "Go to" (:biff/base-url ctx))))

(defn -main [& args]
  (start)
  (apply nrepl-cmd/-main args))
