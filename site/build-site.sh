#!/bin/bash

if [ ! -d themes/hugo-material-docs ]
then
  git clone https://github.com/digitalcraftsman/hugo-material-docs.git themes/hugo-material-docs
  cd themes/hugo-material-docs
  git checkout 194c497216c8389e02e9719381168a668a0ffb05
  cd ../..
fi
hugo --theme hugo-material-docs --baseURL https://iot-hub.dev.saz.bosch-si.com/hono-site

