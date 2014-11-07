#!/bin/sh

lein with-profile dev do clean, cljsbuild once
