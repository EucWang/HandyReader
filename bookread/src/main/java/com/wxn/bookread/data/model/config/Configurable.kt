package com.wxn.bookread.data.model.config

import kotlinx.coroutines.flow.StateFlow

import com.wxn.bookread.data.model.config.Configurable.Preferences
import com.wxn.bookread.data.model.config.Configurable.Settings

/**
 * A [Configurable] is a component with a set of configurable [Settings].
 */
public interface Configurable<S : Settings, P : Preferences<P>> {

    /**
     * Marker interface for the [Settings] properties holder.
     */
    public interface Settings

    /**
     * Marker interface for the [Preferences] properties holder.
     */
    public interface Preferences<P : Preferences<P>> {

        /**
         * Creates a new instance of [P] after merging the values of [other].
         *
         * In case of conflict, [other] takes precedence.
         */
        public operator fun plus(other: P): P
    }

    /**
     * Current [Settings] values.
     */
    public val settings: StateFlow<S>

    /**
     * Submits a new set of [Preferences] to update the current [Settings].
     *
     * Note that the [Configurable] might not update its [settings] right away, or might even ignore
     * some of the provided preferences. They are only used as hints to compute the new settings.
     */
    public fun submitPreferences(preferences: P)
}
