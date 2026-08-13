package org.phantam.fozminespoofapi;

/**
 * Thread-safe provider for accessing the {@link FozminespoofApi} instance.
 * <p>
 * External plugins should use this class to retrieve the API instance:
 * <pre>{@code
 * if (FozmineSpoofProvider.isRegistered()) {
 *     FozminespoofApi api = FozmineSpoofProvider.get();
 *     // Interact with fake players
 * }
 * }</pre>
 *
 * @author Phantam
 * @version 2.0.0
 */
public final class FozmineSpoofProvider {

    private static FozminespoofApi instance;

    private FozmineSpoofProvider() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Gets the active {@link FozminespoofApi} instance.
     *
     * @return the API instance
     * @throws IllegalStateException if the API has not been initialized yet
     */
    public static FozminespoofApi get() {
        if (instance == null) {
            throw new IllegalStateException("FozmineSpoof API is not registered yet! Is FozmineSpoof loaded?");
        }
        return instance;
    }

    /**
     * Registers the API implementation.
     * Called internally by the core plugin during startup.
     *
     * @param api the API implementation instance
     */
    public static void register(FozminespoofApi api) {
        if (api == null) {
            throw new IllegalArgumentException("API implementation cannot be null");
        }
        instance = api;
    }

    /**
     * Unregisters the API implementation.
     * Called internally during plugin shutdown.
     */
    public static void unregister() {
        instance = null;
    }

    /**
     * Checks whether the API is registered and ready to use.
     *
     * @return {@code true} if API is available, {@code false} otherwise
     */
    public static boolean isRegistered() {
        return instance != null;
    }
}