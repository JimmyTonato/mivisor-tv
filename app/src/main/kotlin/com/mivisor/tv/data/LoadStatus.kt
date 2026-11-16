package com.mivisor.tv.data

/**
 * 4q · Estado de una carga para enseñarlo (Biblioteca, logos de los canales, banners): si está trabajando, qué dice
 * y si ha fallado. Lo pinta el indicador de la barra de arriba y Ajustes › Biblioteca › «Qué se ha cargado».
 */
data class LoadStatus(val busy: Boolean = false, val text: String = "Pendiente", val error: Boolean = false, val done: Boolean = false) {
    companion object {
        fun working(text: String) = LoadStatus(busy = true, text = text)
        fun ok(text: String) = LoadStatus(text = text, done = true)
        fun failed(text: String) = LoadStatus(text = text, error = true)
    }
}
