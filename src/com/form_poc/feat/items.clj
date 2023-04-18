(ns com.form-poc.feat.items
  (:require [com.biffweb :as biff :refer [q lookup]]
            [com.form-poc.middleware :as mid]
            [com.form-poc.ui :as ui]
            [xtdb.api :as xt]
            [malli.core :as m]
            [malli.transform :as mt]
            [malli.error :as me]))

(defn item-form [{:keys [id name qty form-errors]}]
  [:div {:class "w-full max-w-xs"}
   (biff/form {:class "bg-white shadow-md rounded px-8 pt-6 pb-8 mb-4" :hx-post "/items/save-item" :hx-swap "outerHTML"}
              [:input {:id "id" :name "id" :type "hidden" :value id}]
              [:div.mb-4
               [:label {:class "block text-gray-700 text-sm font-bold mb-2" :for "name"} "Name:"]
               [:input {:class "shadow appearance-none border rounded w-full py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline"
                        :id "name" :name "name" :type "text" :placeholder "Name" :value name}]
               [:p {:class "text-red-500 text-xs italic"} (-> form-errors :item/name first)]]
              [:div.mb-6
               [:label {:class "block text-gray-700 text-sm font-bold mb-2" :for "qty"} "Quantity:"]
               [:input {:class "shadow appearance-none border rounded w-full py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline"
                        :id "qty" :name "qty" :type "number" :placeholder "Quantity" :value qty}]
               [:p {:class "text-red-500 text-xs italic"} (-> form-errors :item/qty first)]]
              [:div {:class "flex items-center justify-between"}
               [:button
                {:class "bg-blue-500 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded focus:outline-none focus:shadow-outline" :type "submit"} "Save"]
               [:a
                {:class "text-white bg-blue-700 hover:bg-blue-800 focus:ring-4 focus:ring-blue-300 font-medium rounded-lg text-sm px-5 py-2.5 mr-2 mb-2 dark:bg-blue-600 dark:hover:bg-blue-700 focus:outline-none dark:focus:ring-blue-800" :href "/items"} "Cancel"]])])

(defn message-dialog [msg]
  (ui/page
   {}
   [:div {:class "w-full max-w-xs"}
    [:div.mb-4
     [:p msg]]
    [:div.mb-6
     [:div {:class "flex items-center justify-between"}
      [:a
       {:class "text-white bg-blue-700 hover:bg-blue-800 focus:ring-4 focus:ring-blue-300 font-medium rounded-lg text-sm px-5 py-2.5 mr-2 mb-2 dark:bg-blue-600 dark:hover:bg-blue-700 focus:outline-none dark:focus:ring-blue-800" :href "/items"} "Close"]]]]))

(defn load-item-id [form db param-id]
  (let [id (parse-uuid param-id)]
    (if-let [item (lookup db :xt/id id)]
      (let [{:keys [item/name item/qty]} item]
        (ui/page
         {}
         nil
         (form {:id id :name name :qty qty :db db})))
      (message-dialog "No record found."))))

(defn view-history [record] 
  (let [tx-time (:xtdb.api/tx-time record)
        history-doc (:xtdb.api/doc record)
        name (:item/name history-doc)
        qty (:item/qty history-doc)]
    [:li [:p  "On " [:strong tx-time] " Record name= " name " qty= " qty]]))

(defn item-form-view [{:keys [id name qty db]}]
  [:div {:class "w-full max-w-xs"}
   (biff/form {:class "bg-white shadow-md rounded px-8 pt-6 pb-8 mb-4" :hx-get "#"}
              [:input {:id "id" :name "id" :type "hidden" :value id}]
              [:div.mb-4
               [:label {:class "block text-gray-700 text-sm font-bold mb-2" :for "name"} "Name:"]
               [:input {:class "shadow appearance-none border rounded w-full py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline cursor-not-allowed"
                        :id "name" :name "name" :type "text" :placeholder "Name" :value name :disabled true}]]
              [:div.mb-6
               [:label {:class "block text-gray-700 text-sm font-bold mb-2" :for "qty"} "Quantity:"]
               [:input {:class "shadow appearance-none border rounded w-full py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline cursor-not-allowed"
                        :id "qty" :name "qty" :type "number" :placeholder "Quantity" :value qty :disabled true}]]
              [:div {:class "flex items-center justify-between"}
               [:a
                {:class "text-white bg-blue-700 hover:bg-blue-800 focus:ring-4 focus:ring-blue-300 font-medium rounded-lg text-sm px-5 py-2.5 mr-2 mb-2 dark:bg-blue-600 dark:hover:bg-blue-700 focus:outline-none dark:focus:ring-blue-800" :href "/items"} "Close"]])
   [:div.mb-4 
    (let [history-list (xt/entity-history db id :desc {:with-docs? true})]
      [:ul
       (map view-history history-list)])]])

(defn item-view-page [{:keys [biff/db query-params]}]
  (if-let [param-id (query-params "id")]
    (load-item-id item-form-view db param-id)
    (ui/page
     {}
     nil
     (message-dialog "No item id."))))

(defn item-form-page [{:keys [biff/db query-params] :as req}]
  (if-let [param-id (query-params "id")]
    (load-item-id item-form db param-id)
    (ui/page
     {}
     nil
     (item-form req))))

(defn save-item [{:keys [params] :as req}]
  (let [{:keys [id name qty]} params
        item-id (if (empty? id)
                  (random-uuid)
                  (parse-uuid id))
        item-schema [:map {:closed true}
                     [:db/doc-type :keyword]
                     [:db/op :keyword]
                     [:xt/id :uuid]
                     [:item/name [:string {:min 1}]]
                     [:item/qty :int]]
        qty-int (m/decode int? qty mt/string-transformer)
        doc {:db/doc-type :item
             :db/op :merge
             :xt/id item-id
             :item/name name
             :item/qty qty-int}]
    (if (m/validate
         item-schema doc)
      (do
        (biff/submit-tx req
                        [doc])
        (message-dialog "Item saved."))
      (let [form-errors (-> item-schema (m/explain doc) (me/humanize))]
        (biff/pprint form-errors)
        (biff/render (item-form {:name name :qty qty :form-errors form-errors}))))))


(defn item-form-delete [{:keys [id name qty]}]
  [:div {:class "w-full max-w-xs"}
   (biff/form {:class "bg-white shadow-md rounded px-8 pt-6 pb-8 mb-4" :hx-post "/items/delete-item" :hx-swap "outerHTML"}
              [:input {:id "id" :name "id" :type "hidden" :value id}]
              [:div.mb-4
               [:label {:class "block text-gray-700 text-sm font-bold mb-2" :for "name"} "Name:"]
               [:input {:class "shadow appearance-none border rounded w-full py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline cursor-not-allowed"
                        :id "name" :name "name" :type "text" :placeholder "Name" :value name :disabled true}]]
              [:div.mb-6
               [:label {:class "block text-gray-700 text-sm font-bold mb-2" :for "qty"} "Quantity:"]
               [:input {:class "shadow appearance-none border rounded w-full py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline cursor-not-allowed"
                        :id "qty" :name "qty" :type "number" :placeholder "Quantity" :value qty :disabled true}]]
              [:div {:class "flex items-center justify-between"}
               [:button
                {:class "bg-red-500 hover:bg-red-700 text-white font-bold py-2 px-4 rounded focus:outline-none focus:shadow-outline" :type "submit"} "Delete"]
               [:a
                {:class "text-white bg-blue-700 hover:bg-blue-800 focus:ring-4 focus:ring-blue-300 font-medium rounded-lg text-sm px-5 py-2.5 mr-2 mb-2 dark:bg-blue-600 dark:hover:bg-blue-700 focus:outline-none dark:focus:ring-blue-800" :href "/items"} "Cancel"]])])

(defn item-form-delete-page [{:keys [biff/db query-params]}]
  (if-let [param-id (query-params "id")]
    (load-item-id item-form-delete db param-id)
    (ui/page
     {}
     nil
     (message-dialog "No item id."))))

(defn delete-item [{:keys [params] :as req}]
  (let [{:keys [id]} params
        item-id (parse-uuid id)
        doc {:db/op :delete
             :xt/id item-id}]
    (biff/submit-tx req
                    [doc])
    (message-dialog "Item deleted.")))

(defn list-item [{:keys [xt/id item/name item/qty]}]
  [:tr
   [:td.border.px-4.py-2 name]
   [:td.border.px-4.py-2 qty]
   [:td.border.px-4.py-2
    [:div {:class "inline-flex gap-5"}
     [:a {:class "bg-blue-500 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded" :href (str "/items/view?id=" id)} "View "]
     [:a {:class "bg-orange-500 hover:bg-orange-700 text-white font-bold py-2 px-4 rounded" :href (str "/items/edit?id=" id)} "Edit "]
     [:a {:class "bg-red-500 hover:bg-red-700 text-white font-bold py-2 px-4 rounded" :href (str "/items/delete?id=" id)} "Delete "]]]])

(defn items-table [items]
  [:table.table-auto
   [:thead
    [:tr
     [:th.border.px-4.py-2 "Name"]
     [:th.border.px-4.py-2 "Quantity"]
     [:th.border.px-4.py-2 "Operations"]]]
   [:tbody
    (map list-item items)]])

(defn parse-page [page]
  (let [re-find (re-find #"\A-?\d+" page)
        page (Integer/parseInt re-find)]
    (if (<= page 0) 1 page)))

(defn items [{:keys [query-params session biff/db]}]
  (let [{:user/keys [email]} (xt/entity db (:uid session))
        page-num (parse-page (query-params "page" "1"))]
    (ui/page
     {}
     nil
     [:div "Signed in as " email ". "
      (biff/form
       {:action "/auth/signout"
        :class "inline"}
       [:button.text-blue-500.hover:text-blue-800 {:type "submit"}
        "Sign out"])
      "."]
     [:.h-6]
     [:a {:class "font-medium text-blue-600 dark:text-blue-500 hover:underline" :href "/items/create"} "Create Item"]
     [:.h-6]
     (let [max-items 3
           offset (- (* max-items page-num) max-items)
           query '{:find (pull item [*])
                   :where [[item :item/name]]}
           query-p (assoc query :offset offset :limit max-items)
           items (q db
                    query-p)]
       (items-table items))
     [:.h-6]
     [:div.mb-6
      [:a {:href (str "/items?page=" (dec page-num)) :class "inline-flex items-center px-4 py-2 text-sm font-medium text-gray-500 bg-white border border-gray-300 rounded-lg hover:bg-gray-100 hover:text-gray-700 dark:bg-gray-800 dark:border-gray-700 dark:text-gray-400 dark:hover:bg-gray-700 dark:hover:text-white"} "Previous"]
      [:a {:href (str "/items?page=" (inc page-num)) :class "inline-flex items-center px-4 py-2 text-sm font-medium text-gray-500 bg-white border border-gray-300 rounded-lg hover:bg-gray-100 hover:text-gray-700 dark:bg-gray-800 dark:border-gray-700 dark:text-gray-400 dark:hover:bg-gray-700 dark:hover:text-white"} "Next"]])))

(def features
  {:routes ["/items" {:middleware [mid/wrap-signed-in]}
            ["" {:get items}]
            ["/create" {:get item-form-page}]
            ["/view" {:get item-view-page}]
            ["/save-item" {:post save-item}]
            ["/edit" {:get item-form-page}]
            ["/delete" {:get item-form-delete-page}]
            ["/delete-item" {:post delete-item}]]})
