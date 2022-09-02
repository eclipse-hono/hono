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

git submodule status
IF ERRORLEVEL 1 (
  ECHO Initializing submodules containing Hugo themes.
  git submodule update --init
)

cd homepage
IF NOT "%~1"==""  (
  ECHO Going to build homepage in directory: %1
  hugo -v -d %1
) ELSE (
  ECHO Going to build homepage in default directory...
  hugo -v
)
cd ..

cd documentation
IF NOT "%~1"==""  (
  ECHO Going to build documentation in directory: %1\docs
  hugo -v -d %1\docs
) ELSE (
  ECHO Going to build documentation in default directory...
  hugo -v
)
cd ..
