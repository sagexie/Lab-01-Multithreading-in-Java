package edu.itc.se.lab01.task1;

public class CountdownTask implements Runnable{
    public void run() { 
        String name = Thread.currentThread().getName();
        
        for (int i = 3; i >= 1; i--) { 
            System.out.println(name + ": " + i); 
            try { Thread.sleep(1000); 

            } catch (InterruptedException e) { 
                Thread.currentThread().interrupt(); 
                return; 
            } 
        }
         System.out.println(name + ": liftoff!"); 
    }

}
