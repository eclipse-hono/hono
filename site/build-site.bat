@rem ***************************************************************************
@rem Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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
SETLOCAL EnableDelayedExpansion
ECHO Going to build documentation...
hugo version
IF ERRORLEVEL 1 (
  ECHO Please install "hugo" to be able to build the hono documentation. See readme.md for further details.
  EXIT 1
) ELSE (
  ECHO Hugo installation detected...
)

cd homepage
SET WEBSITE_THEME_CLONING_REQUIRED=1
IF EXIST themes\hugo-universal-theme\.git (
  SET /p HEAD=<themes\hugo-universal-theme\.git\HEAD
  SET WEBSITE_THEME_HEAD_REVISION=78887402aeca0ab44deb8f0de800f7a6974f8c8a
  ECHO website theme repo rev required: !WEBSITE_THEME_HEAD_REVISION!, rev found: !HEAD!
  IF "!WEBSITE_THEME_HEAD_REVISION!" == "!HEAD!" (
    SET WEBSITE_THEME_CLONING_REQUIRED=0
  )
)
IF "!WEBSITE_THEME_CLONING_REQUIRED!" == "1" (
  RMDIR /S /Q themes\hugo-universal-theme
  ECHO cloning website theme repository...
  git clone --depth 1 --branch 1.3.2 https://github.com/devcows/hugo-universal-theme.git themes\hugo-universal-theme
)

IF NOT "%~1"==""  (
  ECHO Going to build homepage in directory: %1
  hugo -v -d %1
) ELSE (
  ECHO Going to build homepage in default directory...
  hugo -v
)
cd ..

cd documentation
SET DOC_THEME_CLONING_REQUIRED=1
IF EXIST themes\hugo-theme-relearn\.git (
  SET /p HEAD=<themes\hugo-theme-relearn\.git\HEAD
  SET DOC_THEME_HEAD_REVISION=2e30ef1e53f9cfd47fb5d41b19cc0de1de436803
  ECHO doc theme repo rev required: !DOC_THEME_HEAD_REVISION!, rev found: !HEAD!
  IF "!DOC_THEME_HEAD_REVISION!" == "!HEAD!" (
    SET DOC_THEME_CLONING_REQUIRED=0
  )
)
IF "!DOC_THEME_CLONING_REQUIRED!" == "1" (
  RMDIR /S /Q themes\hugo-theme-relearn
  ECHO cloning doc theme repository...
  git clone --depth 1 --branch 4.0.4 https://github.com/McShelby/hugo-theme-relearn.git themes/hugo-theme-relearn
)

IF NOT "%~1"==""  (
  ECHO Going to build documentation in directory: %1\docs
  hugo -v -d %1\docs
) ELSE (
  ECHO Going to build documentation in default directory...
  hugo -v
)
cd ..
