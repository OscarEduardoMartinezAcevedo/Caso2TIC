import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Opcion2 {
    private String lastError;

    public String getLastError() { 
        return lastError; 
    }

    public boolean runOpcion2(int nproc, int marcosTotales, Path inDir) {
        // Input
        int marcosPerProcess = marcosTotales / nproc;
        List<List<Long>> processPages = new ArrayList<>();

        // Cargar datos
        for (int pid = 0; pid < nproc; pid++) {
            Path procFile = inDir.resolve("proc" + pid + ".txt");
            List<Long> pages = loadPages(procFile);
            if (pages == null) return false;
            processPages.add(pages);
        }

        // Crear procesos
        List<Proceso> procesos = new ArrayList<>();
        for (int i = 0; i < nproc; i++) {
            procesos.add(new Proceso(i, processPages.get(i), marcosPerProcess));
        }
        
        System.out.println("Procesos: " + nproc + ", Marcos: " + marcosTotales);
        
        simular(procesos);
        
        for (Proceso p : procesos) {
        int hits = p.referencias - p.fallos;
        double tasaFallos = p.referencias > 0 ? (double) p.fallos / p.referencias : 0.0;
        double tasaExito = p.referencias > 0 ? (double) hits / p.referencias : 0.0;
        
        System.out.println("Proceso: " + p.pid);
        System.out.println("- Num referencias: " + p.referencias);
        System.out.println("- Fallas: " + p.fallos);
        System.out.println("- Hits: " + hits);
        System.out.println("- SWAP: " + p.swapAccesos);
        System.out.printf("- Tasa fallas: %.4f%n", tasaFallos);
        System.out.printf("- Tasa éxito: %.4f%n", tasaExito);
    }
        
        return true;
    }
    
    private void simular(List<Proceso> procesos) {
        Queue<Proceso> cola = new LinkedList<>();
        
        // Inicializar cola con procesos que tienen paginas pendientes
        for (Proceso p : procesos) {
            if (p.tienePaginasPendientes()) {
                cola.add(p);
            }
        }
        
        long tiempoGlobal = 0;
        
        while (!cola.isEmpty()) {
            Proceso proceso = cola.poll();
            
            // Procesar una pagina del proceso
            boolean exitoso = proceso.procesarSiguientePagina(tiempoGlobal);
            tiempoGlobal++;
            
            if (exitoso) {
                if (proceso.tienePaginasPendientes()) {
                    cola.add(proceso);
                } else {
                    reasignarMarcos(proceso, procesos);
                }
            } else {
                // Si hay Fallo de pagina el proceso pierde turno y vuelve a cola
                cola.add(proceso);
            }
        }
    }
    
    private void reasignarMarcos(Proceso terminado, List<Proceso> procesos) {
        if (terminado.marcos.isEmpty()) return;
        
        // Encontrar proceso con mas fallos
        Proceso mayorFallos = null;
        int maxFallos = -1;
        
        for (Proceso p : procesos) {
            if (p != terminado && p.tienePaginasPendientes() && p.fallos > maxFallos) {
                maxFallos = p.fallos;
                mayorFallos = p;
            }
        }
        
        if (mayorFallos != null) {
            mayorFallos.marcos.addAll(terminado.marcos);
            System.out.println("Marcos de proceso " + terminado.pid + 
                             " reasignados a proceso " + mayorFallos.pid);
        }
        terminado.marcos.clear();
    }
    
    // Clase para manejar cada proceso
    private static class Proceso {
        int pid;
        List<Long> paginas;
        int indicePagina = 0;
        
        // Marcos asignados: cada marco contiene pagina + contador aging
        List<Marco> marcos = new ArrayList<>();
        int maxMarcos;
        
        // Estadisticas
        int referencias = 0;
        int fallos = 0;
        int swapAccesos = 0;
        
        Proceso(int pid, List<Long> paginas, int maxMarcos) {
            this.pid = pid;
            this.paginas = paginas;
            this.maxMarcos = maxMarcos;
        }
        
        boolean tienePaginasPendientes() {
            return indicePagina < paginas.size();
        }
        
        boolean procesarSiguientePagina(long tiempo) {
            if (!tienePaginasPendientes()) return true;
            
            long pagina = paginas.get(indicePagina);
            referencias++;
            
            // Buscar pagina en marcos actuales
            Marco marcoHit = null;
            for (Marco m : marcos) {
                if (m.pagina == pagina) {
                    marcoHit = m;
                    break;
                }
            }
            
            if (marcoHit != null) {
                // HIT
                marcoHit.contadorAging = (byte) 0x80; // Bit R = 1
                indicePagina++; // Avanzar a siguiente pagina
                return true;
                
            } else {
                // MISS
                fallos++;
                
                if (marcos.size() < maxMarcos) {
                    // Hay marco libre
                    marcos.add(new Marco(pagina, (byte) 0x80));
                    swapAccesos++; // Un acceso para cargar
                } else {
                    // Reemplazo LRU usando aging
                    Marco lru = encontrarLRU();
                    lru.pagina = pagina;
                    lru.contadorAging = (byte) 0x80;
                    swapAccesos += 2; // Sacar pagina vieja + cargar nueva
                }
                
                // Envejece todo contador despues de cada referencia
                envejecerContadores();
                
                // Pierde turno por fallo de pagina
                return false;
            }
        }
        
        private Marco encontrarLRU() {
            Marco lru = marcos.get(0);
            for (Marco m : marcos) {
                if ((m.contadorAging & 0xFF) < (lru.contadorAging & 0xFF)) {
                    lru = m;
                }
            }
            return lru;
        }
        
        private void envejecerContadores() {
            for (Marco m : marcos) {
                // Desplazar a la derecha 1 bit
                m.contadorAging = (byte) ((m.contadorAging & 0xFF) >> 1);
            }
        }
    }
    
    // Marco: pagina + contador aging de 8 bits
    private static class Marco {
        long pagina;
        byte contadorAging;
        
        Marco(long pagina, byte contador) {
            this.pagina = pagina;
            this.contadorAging = contador;
        }
    }

    private List<Long> loadPages(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            
            // Verifica lineas de metadatos
            if (lines.size() < 5) {
                lastError = "Archivo " + file.getFileName() + " no tiene suficientes líneas de metadatos (mínimo 5)";
                return null;
            }

            List<Long> pages = new ArrayList<>();
            
            // Las referencias empiezan en linea 5
            for (int i = 5; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty()) continue;
                
                String[] parts = line.split(",");
                if (parts.length < 3) {
                    lastError = "Formato inválido en archivo " + file.getFileName() + " línea " + (i+1) + ": se esperan al menos 3 campos";
                    return null;
                }
                
                try {
                    // El numero de pagina calculado usando el segundo campo que contiene la coordenada
                    String coordStr = parts[1].trim();
                    long pagina = Long.parseLong(coordStr);
                    pages.add(pagina);
                } catch (NumberFormatException e) {
                    lastError = "Coordenada inválida en archivo " + file.getFileName() + " línea " + (i+1) + ": " + parts[1];
                    return null;
                }
            }
            
            if (pages.isEmpty()) {
                lastError = "No se encontraron referencias de páginas en " + file.getFileName();
                return null;
            }
            
            return pages;
        } catch (Exception e) {
            lastError = "Error leyendo archivo " + file.getFileName() + ": " + e.getMessage();
            return null;
        }
    }
}