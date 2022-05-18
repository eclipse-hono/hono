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
ECHO Going to build documentation...
hugo version
IF ERRORLEVEL 1 (
  ECHO Please install "hugo" to be able to build the hono documentation. See readme.md for further details.
  EXIT 1
) ELSE (
  ECHO Hugo installation detected...
)

cd homepage
IF NOT EXIST themes\hugo-universal-theme (
  ECHO Going to download theme 'universal' for hugo...
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
IF NOT EXIST themes\hugo-theme-relearn (
  ECHO Going to download theme 'relearn' for hugo...
  git clone --depth 1 --branch 3.4.1 https://github.com/McShelby/hugo-theme-relearn.git themes\hugo-theme-relearn
)

IF NOT "%~1"==""  (
  ECHO Going to build documentation in directory: %1\docs
  hugo -v -d %1\docs
) ELSE (
  ECHO Going to build documentation in default directory...
  hugo -v
)
cd ..
