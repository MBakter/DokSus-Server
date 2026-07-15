package hr.alu.okiru.doksusserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DokSusServerApplication

fun main(args: Array<String>) {
    runApplication<DokSusServerApplication>(*args)
}
