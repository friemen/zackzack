# zackzack

A prototype for a ClojureScript/Om/core.async based in-browser UI.

This is work in progress. I just started with Om (beginning of Nov'14).

A running demo is hosted [here](http://www.falkoriemenschneider.de/zackzack/).


## Motivation

I prefer a strict separation between presentation logic and
markup. The Om examples I'm aware of tend to mix both aspects a bit,
however, IMO using channels as proposed by @swannodette is the key to
strong decoupling.

I'm interested in boring enterprise style forms-over-data UIs. I like
to succinctly specify the visual content of views without mixing it up
with presentation logic. However, things like state, actions,
validation, rules, inter-component and remote communication need a
place. I prefer to implement these using ordinary language features
like atoms and pure functions, perhaps augmented with access to
channels, if necessary.

My hope is to show how the combination of Om and core.async enables
drastically simpler enterprise-style UI development. This is how I
like the code for boring UIs to look alike:

```clojure
;; ============================================================================
;; Address Details


(def fields [:private :name :company :street :city :birthday])


(def address-constraints
  (e/rule-set
   
   [[:name :value]]
   c/required (c/min-length 3)
   
   [[:private :value] [:company :value]]
   (fn [p? c]
     (if p?
       (if (> (count c) 0)
         "Company must be empty")
       (if (empty? c)
         "Company must not be empty")))))


;; ----------------------------------------------------------------------------
;; Actions are functions [state event -> state]

(defn details-add!
  [state event]
  (let [a  (get-all state :value fields)
        i  (-> state :edit-index)]
    (put-view! "addressbook" {:type :action
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
;; All rules are represented by one function [state -> state]

(defn addressdetails-rules
  [state]
  (let [invalid?  (->> state (e/validate address-constraints) (e/has-errors?))
        edit?     (-> state :edit-index)
        private?  (-> state :private :value)]
    (-> state
        (assoc-in [:add :text]         (if edit? "Update"))
        (assoc-in [:title]             (if edit? "Edit Details" "Details"))
        (assoc-in [:company :disabled] private?)
        (update-in [:company :value]   #(if private? "" %))
        (assoc-in [:add :disabled]     (if invalid? true)))))



;; ----------------------------------------------------------------------------
;; A concise "model" of the Addressbook view

(defn addressdetails-view
  []
  (view "details"
        :elements
        [(panel "fields" :layout :two-columns
                :elements [(checkbox "private")
                           (textfield "name" :label "Full name")
                           (textfield "company")
                           (textfield "street")
                           (selectbox "city")
                           (datepicker "birthday")])
         (panel "actions" :elements
                [(button "add" :text "Add Address") (button "reset")])]
        :actions {:add       details-add!
                  :edit      details-edit
                  :reset     details-reset}
        :rules addressdetails-rules
        :constraints address-constraints))



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
      (put-view! "details" {:type :action
                            :id "edit"
                            :address a
                            :index i})))
  state)


(defn <addressbook-delete
  [state event]
  (let [question (str "You're about to delete item "
                      (-> state :addresses :selection first)
                      ". Are you sure?")]
    (go (if (= :ok (<! (<ask question)))
          (remove-selected state [:addresses])
          state))))


(defn <addressbook-reload
  [state event]
  (go (if (= :ok (<! (<ask "This will undo all your local changes. Are you sure?")))
        (let [addresses (:body (<! (http/get "/addresses")))]
          (assoc-in state [:addresses :items] addresses))
        state)))


;; ----------------------------------------------------------------------------
;; Rules are represented by a sole function [state -> state]

(defn addressbook-rules
  [state]
  (let [none-sel?  (-> state :addresses :selection empty?)]
    (-> state
        (assoc-in [:edit :disabled]   none-sel?)
        (assoc-in [:delete :disabled] none-sel?))))



;; ----------------------------------------------------------------------------
;; A concise "model" of the Addressbook view

(defn addressbook-view
  [id]
  (view id
        :elements
        [(addressdetails-view)
         (table "addresses"
                :columns [(column "name")
                          (column "company")
                          (column "street")
                          (column "city")
                          (column "birthday")]
                :actions-fn (fn [item]
                              [(action-link "edit" :image "images/pencil.png")
                               (action-link "delete" :image "images/cross.png")]))
         (button "reload")]
        :actions {:add       addressbook-add
                  :edit      addressbook-edit!
                  :delete    <addressbook-delete
                  :reload    <addressbook-reload}
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

In my setup each view gets its own channel, and all channels are
registered in a central map. Are there any general rules that
determine the process+channel topology?

How can input focus be controlled?  The decision, where to take the
focus to, could be part of an action or, more general, in an
event-handler. React basically supports this:
http://facebook.github.io/react/docs/working-with-the-browser.html
I've seen that Reagents TodoMVC uses .focus within did-mount.


## My ideas and decisions so far

Application state is kept in a global atom, according to Om's pgm
model. Local component state is almost not used, so even "transient"
information like currently selected items or enabled/disabled state of
a component is part of global application state. This makes possible
to directly influence this data in action functions that are not part
of the component to be controlled.

A *view* bundles the specification of visual contents, actions, 
rules and a validation constraints.

Views and view contents is specified as data using a bunch of
functions that create nested maps (see zackzack.specs namespace).

There is a generic *view component* that starts a generic CSP-style
controller process and builds components by interpretation of the
spec-fn result.

Each view component has one input channel that takes every event
submitted by JS event listeners. Any user input is routed as
`:update` event through this channel and the application state is
updated immediately with the result of a field specific *parser*
function [string -> anything] application.

After processing an `:update`, *validation* with respect to the
updated field is applied. This results in an update of all `:message`
values stored in the state maps of input fields.

*Actions* (the stuff that happens for example upon button clicks) are
triggered by `:action` events and should ideally be pure functions of
the form [state event -> (U state channel)]. If they communicate with
another component they use the foreign components channel via
`put-view!`. They usually return the new state of the view (see also
Remote Communication).

*Remote communication* is done asynchronously. An action body can be
wrapped in a `go` block to access a remote service operation. In this
case, the action returns a channel. When this channel emits a message
it is used as new view state in an `:update` message processed by the
responsible view. (This is a bit dangerous, because the user might
apply changes somewhere else which are then overwritten by the
message. The piece of state that gets updated must be narrowed,
alternatively the UI has to be blocked.)

After processing of an `:update` or `:action` event a *rules* function
[state -> state] is called that ensures that invariants regarding
the components state are re-established.

To enable *communication among controller processes* each view has its
own input channel which is created when the view components state is
initialised (IInitState). Upon mounting, the channel is registered
under the views id in a global map. The channel is removed from this
map when it is unmounted. A view used as child of a parent view
contains the channel of its parent in the `:parent-ch` entry in its
spec. An action that wishes to send an event to a different process
would use type `:action`, which, in turn, triggers the execution of an
arbitrary action function.


## TODOs

* Component access to global data like user, roles, rights (can this
  be done through Om's global, immutable shared state?)
* Formatting / parsing of values
* Controlling input focus


## Usage

Clone this repo. Make sure you're on Java 1.7 or higher and have at
least Leiningen 2.5 installed.


### To enter interactive development

* If you have just worked with `cljsbuild auto` and switch to
  interactive mode make sure to delete resources/public/js before
  starting the REPL. You can use `lein cljsbuild clean`.
* Create a REPL session, load `zackzack.backend` namespace.
* `(start!)` starts http-kit web server.
* `(cljs-repl)` starts a browser based ClojureScript REPL. Wait for an
  output like `<< started Weasel server on ws://127.0.0.1:9001 >>`.
  Now the REPL is ready for the browser to connect.
* Load the page http://localhost:8080/testindex.html, it will connect
  to the waiting REPL.
* Open a cljs source file. Re-evaluate some code. If you changed data,
  not just functions, then you also have to re-evaluate the `om/root`
  expression (I wrapped it in a function, so `(refresh)` does the
  trick if you changed the views layout).
* You should be able to see the effect in the browser *without*
  reloading anything. In fact, if you reload in the browser all
  your prior evaluations will be gone.


### To use cljsbuild auto and develop Cljs without REPL connection

`lein with-profile auto do, cljsbuild auto` or execute `./auto.sh`.

You'll find the JS build results in resources/public.  Use the
testindex.html to start the frontend without the backend. If the
backend is started you can use http://localhost:8080/testindex.html.


### To start the backend from a terminal

`lein ring server` will start a process listening on http://localhost:8080/.


### To produce a Cljs frontend to publish

`lein do clean, jar` or execute `./produce.sh`.

You'll find the JS build results in resources/public.  Use the
index.html to start the frontend without the backend. If the backend
is started you can use http://localhost:8080/index.html.


### To produce an Uberjar

`lein uberjar` will create a self-contained Jar that can be
run using `java -jar target/zackzack.jar` and starts a web server
waiting on http://localhost:8080/.


## License

Copyright © 2014 F.Riemenschneider

Distributed under the Eclipse Public License version 1.0.
