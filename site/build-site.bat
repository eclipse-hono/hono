@ECHO off
ECHO Going to build documentation...
hugo version
IF ERRORLEVEL 1 (
  ECHO Please install "hugo" to be able to build the hono documentation. See readme.md for further details.
  EXIT 1
) ELSE (
  ECHO Hugo installation detected...
)

IF NOT EXIST themes\hugo-material-docs (
  ECHO Going to download material theme for hugo...
  git clone https://github.com/digitalcraftsman/hugo-material-docs.git themes/hugo-material-docs
  cd themes\hugo-material-docs
  git checkout 194c497216c8389e02e9719381168a668a0ffb05
  cd ..\..
) ELSE (
  ECHO Hugo material theme detected...
)

IF NOT "%~1"==""  (
  ECHO Going to build docs in directory: %1
  hugo --theme hugo-material-docs -d %1
) ELSE (
  ECHO Going to build docs in default directory...
  hugo --theme hugo-material-docs
)