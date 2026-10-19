# @file shell.nix
# @brief Java 21 offline mod build environment.
# @author XiangX (Modolet) <52348766+Modolet@users.noreply.github.com>
# @date 2026-10-19
{ pkgs ? import <nixpkgs> {} }:
pkgs.mkShell { packages = [ pkgs.jdk21 pkgs.python3 pkgs.google-java-format pkgs.ruff ]; }
