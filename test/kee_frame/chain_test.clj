(ns kee-frame.chain-test
  (:require [clojure.test :refer :all]
            [kee-frame.chain :as chain]
            [clojure.string :as str]
            [re-frame.core :as rf])
  (:import (clojure.lang ExceptionInfo)))

(deftest utils
  (testing "Can produce next step id with namespaced keyword"
    (is (= :ns/id-2 (chain/step-id :ns/id 2))))
  (testing "Can produce next step id with plain keyword"
    (is (= :keyw-2 (chain/step-id :keyw 2)))))

(deftest interceptors
  (testing "Inserts dispatch to next"
    (is (= {:dispatch [:next]}
           (chain/link-effects :next [] chain/links {}))))

  (testing "Throws when only one potential link and it's taken"
    (is (thrown? ExceptionInfo
                 (chain/link-effects :next [] chain/links {:dispatch [:something-bad]}))))

  (testing "Inserts on-success on http"
    (is (= [:next]
           (-> (chain/link-effects :next [] chain/links {:http-xhrio {}})
               (get-in [:http-xhrio :on-success])))))

  (testing "Can use special pointer to next action when explicit params are needed"
    (is (= {:dispatch [:next-event :a :b :c]}
           (chain/replace-pointers :next-event {:dispatch [:kee-frame.core/next :a :b :c]}))))

  (testing "Reports error when on-success or dispatch are specified and none of them point to correct next event"
    (is (thrown? ExceptionInfo
                 (chain/link-effects :next-event [] chain/links {:dispatch   [:something]
                                                                 :http-xhrio {:on-success [:something-else]}}))))
  (testing "Exactly one event dispatches to next in chain."
    (is (= {:dispatch   [:break-out-of-here]
            :http-xhrio {:get        "cnn.com"
                         :on-success [:next-event]}}
           (chain/link-effects :next-event [] chain/links {:dispatch   [:break-out-of-here]
                                                           :http-xhrio {:get "cnn.com"}}))))

  (testing "Will pass its parameters on to next in chain"
    (is (= {:dispatch [:next-event 1 2]}
           (-> {:coeffects {:event [:this-event 1 2]}}
               ((chain/effect-postprocessor :next-event))
               :effects)))

    (is (= {:dispatch [:next-event 1 2 3 4]}
           (-> {:coeffects {:event [:previous-event 1 2]}
                :effects   {:dispatch [:kee-frame.core/next 3 4]}}
               ((chain/effect-postprocessor :next-event))
               :effects)))))

(deftest outer-api
  (testing "Plain chain"
    (let [instructions (chain/collect-event-instructions :my/chain
                                                         [identity
                                                          identity])]
      (is (= [:my/chain :my/chain-1]
             (map :id instructions)))))

  (testing "Bad chain"
    (is (thrown-with-msg? ExceptionInfo #""
                          (chain/collect-event-instructions :my/chain
                                                            ["string-should-not-be-here"]))))

  (testing "Named chain"
    (let [instructions (chain/collect-named-event-instructions
                         [:step-1
                          identity
                          :step-2
                          identity])]
      (is (= [:step-1 :step-2]
             (map :id instructions)))))

  (testing "Bad named chain gives good error message"
    (is (thrown-with-msg? ExceptionInfo #""
                          (chain/collect-named-event-instructions
                            [:step-1
                             identity
                             :step-2])))))