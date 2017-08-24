(ns runners.doo
  (:require [doo.runner :refer-macros [doo-tests]]
            [runners.tests]))

(doo-tests
  'lik-m-aid.core-test)

