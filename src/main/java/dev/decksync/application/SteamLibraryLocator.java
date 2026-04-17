package dev.decksync.application;

import java.util.List;

/**
 * Discovers the Steam libraries installed on the host. Returns an empty list when Steam isn't
 * installed or the libraryfolders registry can't be read — callers treat "no libraries" and "Steam
 * not installed" the same way.
 */
public interface SteamLibraryLocator {

  List<SteamLibrary> locate();
}
