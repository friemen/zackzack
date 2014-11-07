# zackzack

A prototype for a ClojureScript/Om/core.async based in-browser UI.

This is work in progress. I just started with Om (beginning of Nov'14).

A running demo is hosted [here](http://www.falkoriemenschneider.de/zackzack/).


## Motivation

I prefer a strict separation between presentation logic and markup
(the view). The Om examples I'm aware of tend to mix both aspects
a bit, however, IMO using channels as proposed by @swannodette is
the key to strong decoupling.

I'm interested in boring enterprise style forms-over-data UIs. I like
to succinctly specify the content of views without mixing up the
specification with presentation logic. However, things like state,
actions, validation, rules, inter-component and remote communication
need a place. I prefer to implement these using ordinary language
features like atoms and pure functions, perhaps augmented with access
to channels, if necessary.

My hope is to show that the combination of Om and core.async enables
drastically simpler UI development. This is how I like the code for
boring UIs to look alike:

```clojure
;; View channels
;; ----------------------------------------------------------------------------

(def addressbook-ch (chan))
(def addressdetails-ch (chan))


;; Remote access
;; ----------------------------------------------------------------------------

(defn load-addresses
  []
  (GET "/addresses" {:handler #(put! addressbook-ch {:type :action
                                                     :id "addresses"
                                                     :payload %})}))


;; ============================================================================
;; Address Details


(def fields [:private :name :company :street :city :birthday])


;; ----------------------------------------------------------------------------
;; Actions are functions [state event -> state]

(defn details-add!
  [state event]
  (let [a  (get-all state :value fields)
        i  (-> state :edit-index)]
    (put! addressbook-ch {:type :action
                          :id "add"
                          :address a
                          :index i})
    (-> state
        (assoc-in  [:edit-index] nil)
        (update-all :value fields nil))))


(defn details-reset
  [state event]
  (-> state
      (assoc-in  [:edit-index] nil)
      (update-all :value fields nil)
      (update-all :message fields nil)))


(defn details-edit
  [state {:keys [address index]}]
  (-> state
          (assoc-in  [:edit-index] index)
          (update-all :value fields address)
          (update-all :message fields nil)))


;; ----------------------------------------------------------------------------
;; Rules are represented by a sole function [state -> state]

(defn addressdetails-rules
  [state]
  (let [;; TODO use real validation
        invalid?   (->> fields
                        (get-all state :value)
                        (filter #(-> % first #{:name :street :city :birthday}))
                        (map second)
                        (some empty?))
        edit?      (-> state :edit-index)
        private?   (-> state :private :value)]
    (-> state
        (assoc-in [:add :text]         (if edit? "Update"))
        (assoc-in [:title]             (if edit? "Edit Details" "Details"))
        (assoc-in [:company :disabled] private?)
        (assoc-in [:add :disabled]     (if invalid? true)))))



;; ----------------------------------------------------------------------------
;; A concise "model" of the Addressbook view

(defn addressdetails-view
  []
  (view "details"
        :spec-fn
        (fn [state]
          [(checkbox "private")
           (textfield "name" :label "Full name")
           (textfield "company")
           (textfield "street")
           (selectbox "city")
           (datepicker "birthday")
           (button "add" :text "Add Address") (button "reset")])
        :ch addressdetails-ch
        :actions {:add       details-add
                  :edit      details-edit!
                  :reset     details-reset}
        :rules addressdetails-rules))



;; ============================================================================
;; Addressbook


;; ----------------------------------------------------------------------------
;; Actions are functions [state event -> state]

(defn addressbook-add
  [state {:keys [address index]}]
  (prn index address)
  (update-in state [:addresses :items] add-or-replace index address))


(defn addressbook-edit!
  [state event]
  (when-let [i (first (get-in state [:addresses :selection]))]    
    (let [a (get-in state [:addresses :items i])]
      (put! addressdetails-ch {:type :action
                               :id "edit"
                               :address a
                               :index i})))
  state)


(defn addressbook-delete
  [state event]
  (remove-selected state [:addresses]))


(defn addressbook-load
  [state event]
  (load-addresses)
  state)


(defn addressbook-replace
  [state {:keys [payload]}]
  (-> state
      (assoc-in [:addresses :items] payload)))


;; ----------------------------------------------------------------------------
;; Rules are represented by a sole function [state -> state]

(defn addressbook-rules
  [state]
  (let [none-sel?  (-> state :addresses :selection empty?)]
    (-> state
        (assoc-in [:edit :disabled]             none-sel?)
        (assoc-in [:delete :disabled]           none-sel?))))



;; ----------------------------------------------------------------------------
;; A concise "model" of the Addressbook view

(defn addressbook-view
  []
  (view "addressbook"
        :spec-fn
        (fn [state]
          [(addressdetails-view)
           (table "addresses"
                  :label "Addresses"
                  :columns [(column "name")
                            (column "company")
                            (column "street")
                            (column "city")
                            (column "birthday")])
           (button "edit") (button "delete") (button "reload")])
        :ch addressbook-ch
        :actions {:add       addressbook-add
                  :edit      addressbook-edit!
                  :delete    addressbook-delete
                  :reload    addressbook-reload
                  :addresses addressbook-replace}
        :rules addressbook-rules))

```

As you can see there is almost no access to technical APIs left, I
added a thin DSL layer on top of the Om components, you may consider
it a means to succinctly parameterize components. However, it's likely
that a concrete project will have to invent it's own DSL to suit its
needs. The advantage of a DSL layer is that it becomes almost trivial
to create and understand a UI like this.


## Open questions

Here are some points that I have to make up my mind about:

Are there any general rules that determine the process+channel topology?

How can input focus be controlled?  The decision, where to take the
focus to, could be part of an action or, more general, in an
event-handler. React basically supports this:
http://facebook.github.io/react/docs/working-with-the-browser.html


## My ideas and decisions so far

State is kept in a global atom, according to Om's pgm model.

A *view* bundles the specification on contents, a channel, actions, 
rules and a validator.

View contents is specified as data using a bunch of functions that
create nested maps (see zackzack.specs namespace).

There is a generic *view component* that starts a generic CSP-style
controller process and builds components by interpretation of the
spec-fn result.

Each view component has one channel that takes every event
submitted by JS event listeners. Any user input is routed as
`:update` event through this channel and the application state is
updated immediately with the result of a field specific *parser*
function [string -> anything] application.

After processing an `:update`, *validation* with respect to the updated
field is applied (currently not fully implemented). This results in a
`:message` value stored in the fields map.

*Actions* (the stuff that happens for example upon button clicks) are
triggered by `:action` events and should ideally be pure functions of
the form [state event -> state]. If they communicate with a server or
another component they would either use their own or a foreign
components channel, respectively.  In case of accessing channels
action functions are no longer pure, so should be named with a
trailing !.

*Remote communication* is done asynchronously. Upon receipt of the
response the callback puts an `:action` event to the components
channel, where the content of the response is held as `:payload`
value. Thus, payload processing takes the same way as action
processing.

After processing of an `:update` or `:action` event a *rules* function
[state -> state] is called that ensures that invariants regarding
the components state are re-established.

To enable *communication among controller processes* the channels can be
defined before the actions. The channels are passed as part of the
component model to the controller and to render functions. An action
that wishes to send an event to a different process would use type
`:action`, which, in turn, triggers the execution of an arbitrary action
function.


## TODOs
* Panels need layout
* Validation
* Formatting / parsing of values
* Controlling input focus


## Usage

Clone this repo. Make sure you're on Java 1.7 or higher and have at
least Leiningen 2.5 installed.

### To enter interactive development

* If you have just worked with `cljsbuild auto` and switch to
  interactive mode make sure to delete resources/public/js before
  starting the REPL.
* Create a REPL session.
* `(run)` starts an embedded Jetty web server.
* `(browser-repl)` starts a browser based ClojureScript REPL.
* Use the URL displayed by browser-repl and connect with the browser
  to it.
* In a different tab in the same browser instance use
  http://localhost:3000 to request index.html.
* Open a cljs source file. Re-evaluate some code. If you changed data,
  not just functions, then you also have to re-evaluate the `om/root`
  expression (I wrapped it in a function, so `(refresh)` does the
  trick if you changed the views layout).
* You should be able to see the effect in the browser *without*
  reloading anything. In fact, if you reload in the browser all
  your prior evaluations will be gone.


### To produce something to publish

`lein do clean, jar` or execute `./produce.sh`.

You'll find the JS build results in resources/public.
Use the index.html to start the frontend.

### To use cljsbuild auto and develop without REPL connection

`lein with-profile auto do, cljsbuild auto` or execute `./auto.sh`.

You'll find the JS build results in resources/public.
Use the testindex.html to start the frontend.


## License

Copyright © 2014 F.Riemenschneider

Distributed under the Eclipse Public License either version 1.0.
