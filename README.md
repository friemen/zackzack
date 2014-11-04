# zackzack

A prototype for a ClojureScript/Om/core.async based in-browser UI.

This is work in progress. I just started with Om (beginning of Nov'14).

A running demo is hosted [here](http://www.falkoriemenschneider.de/zackzack/)


## Usage

Clone this repo. Make sure you're on Java 1.7 or higher.

### To enter interactive development

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


### To produce a target/public dir in order to publish something

`lein with-profile prod do clean, resource, cljsbuild once` or execute `./produce.sh`.


### To use cljsbuild auto and develop without REPL connection

`lein with-profile dev do resource, cljsbuild auto` or execute `./dev.sh`.


## My ideas and decisions so far


I prefer a strict separation between presentation logic and markup
(the view). The Om examples I'm aware of tend to mix both aspects
a bit, however, IMO using channels as proposed by @swannodette is
the key to strong decoupling.

I'm interested in boring enterprise style forms-over-data UIs.  I
like to be able to succinctly specify the content of components
without mixing it up with presentation logic. However, things like
state, actions, validation, rules, inter-component and remote
communication need their place. I prefer to implement these using
ordinary language features like atoms and pure functions, perhaps
augmented with access to channels, if necessary.

This is how I like to build boring UIs:

```clojure
;; Component channels
;; ----------------------------------------------------------------------------

(def addressbook-ch (chan))


;; ----------------------------------------------------------------------------
;; Actions are functions [state event -> state]

(def fields [:name :street :city :birthday])

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


;; ----------------------------------------------------------------------------
;; Rules are represented by a sole function [state -> state]


(defn addressbook-rules
  [state]
  (let [none-sel?  (-> state :addresses :selection empty?)
        invalid?   (->> fields (get-all (:details state) :value) (vals) (some empty?)) ; TODO validation
        edit?      (-> state :edit-index)]
    (-> state
        (assoc-in [:details :add :text]     (if edit? "Update"))
        (assoc-in [:details :title]         (if edit? "Edit Details" "Details"))
        (assoc-in [:edit :disabled]         none-sel?)
        (assoc-in [:delete :disabled]       none-sel?)
        (assoc-in [:details :add :disabled] (if invalid? true)))))


;; ----------------------------------------------------------------------------
;; A concise "model" of the Addressbook UI


(def addressbook-view
  {:view (panel "addressbook"
                :path nil
                :title "Addressbook"
                :elements [(panel "details"
                                  :title "Details"
                                  :elements [(textfield "name" :label "Full name")
                                             (textfield "street")
                                             (selectbox "city")
                                             (datepicker "birthday")
                                             (button "add" :text "Add Address") (button "reset")])
                           (table "addresses"
                                  :label "Addresses"
                                  :columns [(column "name")
                                            (column "street")
                                            (column "city")
                                            (column "birthday")])
                           (button "edit") (button "delete")])
   :ch addressbook-ch
   :actions {:add    add-address
             :edit   edit-address
             :delete delete-addresses
             :reset  reset-address}
   :rules addressbook-rules})
```


Here are some points that I have to make up my mind about:

Which rules determine the process+channel topology?

By using render functions I come to ask myself if everything needs
to be a React component. Is just the uniformity beneficial enough?

How can input focus be controlled?  The decision, where to take the
focus to, could be part of an action or, more general, in an
event-handler. React basically supports this:
http://facebook.github.io/react/docs/working-with-the-browser.html


Here are my decisions that explain why the code looks the way it
does:

State is kept in a global atom, according to Om's pgm model.

There is a generic form component that starts a generic CSP-style
controller process and renders a model describing the components
UI.

Rendering as well as the controller process is in general separated
from the Om component definition.

Each form component has one channel that takes every event
submitted by JS event listeners. Any user input is routed as
:update event through this channel and the application state is
updated immediately with the result of a field specific parser
function [string -> anything] application.

After processing an :update, validation with respect to the
updated field is applied. This results in a :message value
stored in the fields map (not yet implemented).

Actions (the stuff that happens for example upon button clicks) are
triggered by :action events and should ideally be pure functions of
the form [state event -> state]. If they communicate with a server
or another component they would either use their own or a foreign
components channel, respectively.  In case of accessing channels
action functions are no longer pure, so should be named with a !
suffix.

After processing of an :update or :action event a rules function
[state -> state] is called that ensures that invariants are
re-established.

To enable communication among controller processes the channels are
defined before the actions. The channels are passed as
part of the component model to the controller and to render
functions. An action that wishes to send an event to a different
process would use type :action, which, in turn, triggers the execution
of an arbitrary action function.


## TODO
* Validation
* Formatting / parsing of values
* Async invocation of services
* Inter-component communication
* Controlling input focus



## License

Copyright © 2014 F.Riemenschneider

Distributed under the Eclipse Public License either version 1.0.
