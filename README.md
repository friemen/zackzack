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
;; View channel
;; ----------------------------------------------------------------------------

(def addressbook-ch (chan))


;; Remote access
;; ----------------------------------------------------------------------------

(defn load-addresses
  []
  (GET "/addresses" {:handler #(put! addressbook-ch {:type :action
                                                     :id "addresses"
                                                     :payload %})}))

;; ----------------------------------------------------------------------------
;; Actions are functions [state event -> state]

(def fields [:private :name :company :street :city :birthday])

(defn add-address
  [state event]
  (let [a  (get-all (:details state) :value fields)
        i  (-> state :edit-index)]
    (-> state
        (update-in [:addresses :items] add-or-replace i a)
        (assoc-in  [:edit-index] nil)
        (update-in [:details] update-all :value fields nil))))


(defn edit-address
  [state event]
  (if-let [i (first (get-in state [:addresses :selection]))]    
    (let [a (get-in state [:addresses :items i])]
      (-> state
          (assoc-in  [:edit-index] i)
          (update-in [:details] update-all :value fields a)
          (update-in [:details] update-all :message fields nil)))
    state))


(defn reset-address
  [state event]
  (-> state
      (assoc-in  [:edit-index] nil)
      (update-in [:details] update-all :value fields nil)
      (update-in [:details] update-all :message fields nil)))


(defn delete-addresses
  [state event]
  (remove-selected state [:addresses]))


(defn reload-addresses
  [state event]
  (load-addresses)
  state)


(defn replace-addresses
  [state {:keys [payload]}]
  (-> state
      (assoc-in [:addresses :items] payload)))


;; ----------------------------------------------------------------------------
;; Rules are represented by a sole function [state -> state]


(defn addressbook-rules
  [state]
  (let [none-sel?  (-> state :addresses :selection empty?)
        ;; TODO use real validation
        invalid?   (->> fields
                        (get-all (:details state) :value)
                        (filter #(-> % first #{:name :street :city :birthday}))
                        (map second)
                        (some empty?))
        edit?      (-> state :edit-index)
        private?   (-> state :details :private :value)]
    (-> state
        (assoc-in [:details :add :text]         (if edit? "Update"))
        (assoc-in [:details :title]             (if edit? "Edit Details" "Details"))
        (assoc-in [:details :company :disabled] private?)
        (assoc-in [:edit :disabled]             none-sel?)
        (assoc-in [:delete :disabled]           none-sel?)
        (assoc-in [:details :add :disabled]     (if invalid? true)))))


;; ----------------------------------------------------------------------------
;; A concise "model" of the Addressbook UI


(defn addressbook-view
  [state]
  {:spec-fn
   (fn [state]
     (panel "addressbook"
            :elements [(panel "details"
                              :elements [(checkbox "private")
                                         (textfield "name" :label "Full name")
                                         (textfield "company")
                                         (textfield "street")
                                         (selectbox "city")
                                         (datepicker "birthday")
                                         (button "add" :text "Add Address") (button "reset")])
                       (table "addresses"
                              :label "Addresses"
                              :columns [(column "name")
                                        (column "company")
                                        (column "street")
                                        (column "city")
                                        (column "birthday")])
                       (button "edit") (button "delete") (button "reload")]))
   :ch addressbook-ch
   :actions {:add       add-address
             :edit      edit-address
             :delete    delete-addresses
             :reset     reset-address
             :reload    reload-addresses
             :addresses replace-addresses}
   :rules addressbook-rules})
```

As you can see that there is almost no access to technical APIs left,
I added a thin DSL layer on top of the React DOM API, you may consider
it a means to succinctly parameterize Om components. However, it's
likely that a concrete project will have to invent it's own DSL to
suit its needs. The advantage of a DSL layer is that it becomes almost
trivial to create and understand a UI like this.


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
* Inter-component communication
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

You'll find the build results in resources/public.
Use the index.html to start the frontend.

### To use cljsbuild auto and develop without REPL connection

`lein with-profile auto do, cljsbuild auto` or execute `./auto.sh`.

You'll find the build results in resources/public.
Use the testindex.html to start the frontend.


## License

Copyright © 2014 F.Riemenschneider

Distributed under the Eclipse Public License either version 1.0.
