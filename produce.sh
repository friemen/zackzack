#!/bin/sh

lein with-profile prod do clean, resource, cljsbuild once 2>/dev/null
