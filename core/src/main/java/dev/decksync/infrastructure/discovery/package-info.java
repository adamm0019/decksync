/**
 * mDNS peer advertisement and discovery adapters. Lives alongside {@code infrastructure.net} rather
 * than inside it because jmDNS is a self-contained transport — it owns its own sockets and threads
 * and doesn't layer on top of the HTTP client.
 */
package dev.decksync.infrastructure.discovery;
