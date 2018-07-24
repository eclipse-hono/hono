@rem ***************************************************************************
@rem Copyright (c) 2016, 2017 Contributors to the Eclipse Foundation
@rem
@rem See the NOTICE file(s) distributed with this work for additional
@rem information regarding copyright ownership.
@rem
@rem This program and the accompanying materials are made available under the
@rem terms of the Eclipse Public License 2.0 which is available at
@rem http://www.eclipse.org/legal/epl-2.0
@rem
@rem SPDX-License-Identifier: EPL-2.0
@rem ***************************************************************************
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