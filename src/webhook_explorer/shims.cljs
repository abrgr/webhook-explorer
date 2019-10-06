(ns webhook-explorer.shims)

; amazon-cognito-auth-js refers to "global"
(set! (.-global js/window) js/window)
