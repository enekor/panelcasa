package com.n3k0chan.panelcasa.renfe

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * GTFS sintético con los casos difíciles:
 * - Línea C1 de paso por «Sol» (S) en dos sentidos, con un tren semidirecto que se salta «Chamartín».
 * - Nudo «Hub» (H) con tres ramales → modo B.
 * - Cabecera «Terminal» (T) con un único sentido de salida → modo B.
 * - Servicio laborable con festivo quitado, servicio de fin de semana y servicio solo del festivo.
 * - Un tren que sale a las 24:30 del día de servicio.
 * - Campos con espacios de relleno, como el GTFS de Renfe, y un viaje con stop_sequence desordenado.
 */
object GtfsFixture {
    val files = mapOf(
        "agency.txt" to """
            agency_id,agency_name,agency_url,agency_timezone,agency_lang
            1071VC,Renfe Cercanias,https://www.renfe.com,Europe/Madrid,es
        """,
        "stops.txt" to """
            stop_id,stop_name,stop_lat,stop_lon,wheelchair_boarding
            A,Alcalá,40.48,-3.36,1
            B,Bravo,40.45,-3.50,1
            S,Sol,40.4169,-3.7035,1
            C,Chamartín,40.47,-3.68,1
            D,Delicias,40.40,-3.69,1
            H,Hub,41.38,2.14,1
            X1,Norte 1,41.40,2.15,1
            X1B,Norte 2,41.41,2.16,1
            X2,Este 1,41.39,2.18,1
            X2B,Este 2,41.39,2.19,1
            X3,Sur 1,41.36,2.13,1
            X3B,Sur 2,41.35,2.12,1
            T,Terminal,39.47,-0.37,1
            U,Intermedia,39.46,-0.38,1
            V,Final,39.45,-0.39,1
            Z,Sin servicio,40.00,-3.00,0
        """,
        "routes.txt" to """
            route_id,route_short_name,route_long_name,route_type,route_color,route_text_color
            10T0001C1  ,C1  ,Alcalá-Delicias  ,2,75B6E0,FFFFFF
            10T0002C1  ,C1  ,Delicias-Alcalá  ,2,75B6E0,FFFFFF
            51T0001R2  ,R2  ,Hub-Norte  ,2,4CA046,FFFFFF
            51T0002R3  ,R3  ,Hub-Este  ,2,E3363B,FFFFFF
            51T0003R4  ,R4  ,Hub-Sur  ,2,F7A30E,000000
            40T0001C3  ,C3  ,Terminal-Final  ,2,,
        """,
        "calendar.txt" to """
            service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date
            WK,1,1,1,1,1,0,0,20261101,20261231
            WE,0,0,0,0,0,1,1,20261101,20261231
        """,
        "calendar_dates.txt" to """
            service_id,date,exception_type
            WK,20261208,2
            FEST,20261208,1
        """,
        "trips.txt" to """
            route_id,service_id,trip_id,trip_headsign,wheelchair_accessible,block_id,shape_id
            10T0001C1,WK,f1,       ,1,,
            10T0001C1,WK,f2,       ,1,,
            10T0002C1,WK,r1,       ,1,,
            10T0002C1,WK,r2,       ,1,,
            10T0001C1,WE,f3,       ,1,,
            10T0001C1,FEST,f4,       ,1,,
            51T0001R2,WK,h1,       ,1,,
            51T0002R3,WK,h2,       ,1,,
            51T0003R4,WK,h3,       ,1,,
            51T0001R2,WK,h4,       ,1,,
            40T0001C3,WK,t1,       ,1,,
            40T0001C3,WK,t2,       ,1,,
        """,
        "stop_times.txt" to """
            trip_id,arrival_time,departure_time,stop_id,stop_sequence
            f1,07:40:00,07:40:00,A,001
            f1,07:50:00,07:50:00,B,002
            f1,07:59:00,08:00:00,S,003
            f1,08:05:00,08:05:00,C,004
            f1,08:12:00,08:12:00,D,005
            f2,08:20:00,08:20:00,A,001
            f2,08:25:00,08:25:00,B,002
            f2,08:30:00,08:30:00,S,003
            f2,08:40:00,08:40:00,D,004
            r1,08:00:00,08:00:00,D,001
            r1,08:10:00,08:10:00,S,003
            r1,08:05:00,08:05:00,C,002
            r1,08:20:00,08:20:00,B,004
            r1,08:30:00,08:30:00,A,005
            r2,24:20:00,24:20:00,D,001
            r2,24:25:00,24:25:00,C,002
            r2,24:30:00,24:30:00,S,003
            r2,24:40:00,24:40:00,B,004
            r2,24:50:00,24:50:00,A,005
            f3,09:50:00,09:50:00,B,001
            f3,10:00:00,10:00:00,S,002
            f3,10:05:00,10:05:00,C,003
            f3,10:10:00,10:10:00,D,004
            f4,10:50:00,10:50:00,B,001
            f4,11:00:00,11:00:00,S,002
            f4,11:05:00,11:05:00,C,003
            f4,11:10:00,11:10:00,D,004
            h1,09:00:00,09:00:00,H,001
            h1,09:05:00,09:05:00,X1,002
            h1,09:10:00,09:10:00,X1B,003
            h2,09:02:00,09:02:00,H,001
            h2,09:07:00,09:07:00,X2,002
            h2,09:12:00,09:12:00,X2B,003
            h3,09:04:00,09:04:00,H,001
            h3,09:09:00,09:09:00,X3,002
            h3,09:14:00,09:14:00,X3B,003
            h4,09:20:00,09:20:00,X1B,001
            h4,09:25:00,09:25:00,X1,002
            h4,09:30:00,09:30:00,H,003
            t1,06:00:00,06:00:00,T,001
            t1,06:10:00,06:10:00,U,002
            t1,06:20:00,06:20:00,V,003
            t2,07:00:00,07:00:00,V,001
            t2,07:10:00,07:10:00,U,002
            t2,07:20:00,07:20:00,T,003
        """,
    )

    fun write(dir: File, name: String = "gtfs.zip", overrides: Map<String, String?> = emptyMap()): File {
        val f = File(dir, name)
        ZipOutputStream(f.outputStream()).use { zip ->
            for ((entry, content) in files + overrides) {
                if (content == null) continue
                zip.putNextEntry(ZipEntry(entry))
                zip.write((content.trimIndent() + "\r\n").replace("\n", "\r\n").toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return f
    }
}
