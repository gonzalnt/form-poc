(ns com.form-poc.schema)

(def schema
  {:user/id :uuid
   :user/email :string
   :user/joined-at inst?
   :user [:map {:closed true}
          [:xt/id :user/id]
          :user/email
          :user/joined-at]

   :item/id :uuid
   :item/name :string
   :item/qty :int
   :item [:map {:closed true}
          [:xt/id :item/id]
          :item/name
          :item/qty]
   
   :msg/id :uuid
   :msg/user :user/id
   :msg/text :string
   :msg/sent-at inst?
   :msg [:map {:closed true}
         [:xt/id :msg/id]
         :msg/user
         :msg/text
         :msg/sent-at]})

(def features
  {:schema schema})
