package com.ralenday.memoflash.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FusionNubeTest {

    @Test
    fun ganaLaCopiaMasNuevaYElHistorialSeUne() {
        val mazo = Mazo(uuid = "m", nombre = "Local", actualizado = 10)
        val tarjeta = Tarjeta(uuid = "t", mazoUuid = "m", frente = "A", reverso = "B", actualizado = 10)
        val local = Instantanea(
            mazos = listOf(mazo),
            tarjetas = listOf(tarjeta),
            repasos = listOf(repaso("r1", fecha = 1)),
        )
        val remoto = Instantanea(
            mazos = listOf(mazo.copy(nombre = "Nube", actualizado = 20)),
            tarjetas = listOf(tarjeta),
            repasos = listOf(repaso("r2", fecha = 2)),
        )

        val unido = fusionar(local, remoto)

        assertEquals("Nube", unido.mazos.single().nombre)
        assertEquals(listOf("r1", "r2"), unido.repasos.map { it.uuid })
    }

    @Test
    fun unBorradoPosteriorEliminaYUnaEdicionPosteriorLoRevive() {
        val mazo = Mazo(uuid = "m", nombre = "Mazo", actualizado = 10)
        val borrado = Instantanea(borrados = listOf(Borrado("mazos", "m", fecha = 15)))

        val eliminado = fusionar(Instantanea(mazos = listOf(mazo)), borrado)
        assertTrue(eliminado.mazos.isEmpty())
        assertEquals(1, eliminado.borrados.size)

        val editado = mazo.copy(nombre = "Otra vez", actualizado = 30)
        val revivido = fusionar(Instantanea(mazos = listOf(editado)), borrado)
        assertEquals("Otra vez", revivido.mazos.single().nombre)
        assertTrue(revivido.borrados.isEmpty())
    }

    @Test
    fun borrarUnMazoQuitaSusTarjetasYRepasos() {
        val unido = fusionar(
            Instantanea(
                mazos = listOf(Mazo(uuid = "m", nombre = "M", actualizado = 5)),
                tarjetas = listOf(Tarjeta(uuid = "t", mazoUuid = "m", frente = "A", reverso = "B", actualizado = 40)),
                repasos = listOf(repaso("r", fecha = 3)),
            ),
            Instantanea(borrados = listOf(Borrado("mazos", "m", fecha = 20))),
        )
        assertTrue(unido.mazos.isEmpty())
        assertTrue(unido.tarjetas.isEmpty())
        assertTrue(unido.repasos.isEmpty())
    }

    private fun repaso(uuid: String, fecha: Long) = Repaso(
        uuid = uuid,
        tarjetaUuid = "t",
        fecha = fecha,
        calificacion = Calificacion.FACIL,
        intervaloResultante = 3.0,
        origen = Origen.TELEFONO,
    )
}
