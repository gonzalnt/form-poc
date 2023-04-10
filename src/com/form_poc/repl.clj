(ns com.form-poc.repl
  (:require [com.biffweb :as biff :refer [q lookup]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]))

(defn get-sys []
  (biff/assoc-db @biff/system))

(defn add-fixtures []
  (biff/submit-tx (get-sys)
                  (-> (io/resource "fixtures.edn")
                      slurp
                      edn/read-string)))

(defn lookup-entity [str-id]
  (let [{:keys [biff/db]} (get-sys)
        uuid (parse-uuid str-id)]
    (lookup db :xt/id uuid)))

(comment

  ;; As of writing this, calling (biff/refresh) with Conjure causes stdout to
  ;; start going to Vim. fix-print makes sure stdout keeps going to the
  ;; terminal. It may not be necessary in your editor.
  (biff/fix-print (biff/refresh))

  ;; Call this in dev if you'd like to add some seed data to your database. If
  ;; you edit the seed data (in resources/fixtures.edn), you can reset the
  ;; database by running `rm -r storage/xtdb` (DON'T run that in prod),
  ;; restarting your app, and calling add-fixtures again.
  (add-fixtures)

  (let [{:keys [biff/db] :as sys} (get-sys)]
    (q db
       '{:find (pull user [*])
         :where [[user :user/email]]}))

  (let [{:keys [biff/db] :as sys} (get-sys)]
    (q db
       '{:find (pull item [*])
         :where [[item :item/name]]}))

  (lookup-entity "3ceb456c-7b6d-4bf5-8d99-096974f097ed")

  (sort (keys @biff/system))

  ;; Check the terminal for output.
  (biff/submit-job (get-sys) :echo {:foo "bar"})
  (deref (biff/submit-job-for-result (get-sys) :echo {:foo "bar"})))
