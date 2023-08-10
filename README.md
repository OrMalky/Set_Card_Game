# Multi-Threaded Set Card Game
Welcome to the Multi-Threaded Set Card Game repository! This project is an implementation of the popular card game "Set" in Java, utilizing multi-threading to add an exciting twist to the gameplay. if you are interested in multi-threading concepts, this project offers an enjoyable and educational experience.  
![](https://ds055uzetaobb.cloudfront.net/brioche/uploads/BvTYFRoFIy-possibleset.png?width=1200)

## Project Goals
* Practice and experience coding a multi-threaded system
* Practice usage of different multi-threaded tools:
  - Object locks & Semaphore
  - Synchronized functions
  - Concurrent ADTs
* Practice managing a project and testing using MAVEN 

## Table of Contetns
* [Project Overview](#project-overview)
* [Features Overview](#features-overview)
* [Features In-Depth](#features-in-depth)

## Project Overview  
### The Game  
The Set card game is a captivating pattern recognition game where players search for sets of three cards that satisfy specific criteria. These criteria include features like shape, color, number, and shading.

### This Project  
This repository showcases a Java implementation of the Set card game, enhanced with a multi-threaded design.  

### Features Overview
* Multi-threaded design
* Up to 2 Human players
* Up to 4 COM player
* [Config file](src/main/resources/config.properties) for changing different settings
* Dynamic design for easy future changes or additions
* Real time UI for the game

### Features In-Depth
#### Multi-threaded design  
The main goal of this project was to get hands on experience with multi-threaded code design. Each game has the following threads:
* **Table** - Holds the cards.
* **Dealer** - In charge of the deck, shuffling and dealing cards to the table, checking sets and feedbacking players accordingly.
* **Player** - Handles input, commits actions to the table and recieves feedback from the dealer (one for each player).
* **AI** - Generates input for the Player thread (one for each COM player).
* **UI** - Displays and Updates the UI.

Notice that having multiple players commit actions to the same cards in this multi-threaded enviroment requires delicate work, since it poses a lot of threats. For example, one player chooses a valid set to be checked by the dealer, and before the dealer do so another player chooses a set with one or more simillar cards to be checked too. When resolved by the dealer, he will have to replace the cards of the first set (since it was a valid set) and now the second player's set is no longer there. To get around these problems we used different methods:
* Object locking - allowed us to lock the usage of different objects when mid action (mainly using the Semaphore).
* Semaphore - using the built-in Semaphore class of Java we can allocate different amount of keys for the locked objects, and also make sure that the waiting queue is fair (FIFO) and not random.
* Synchronized functions - allowed us to make sure some functions cannot be interrupted until finished.
* Concurrent Data Structures - made sure that some data structres does not change while in use by different objects.

#### Players
The game supports up to 4 players. These can be split however you like between up to 2 human players and up to 4 COM players via the [config file](src/main/resources/config.properties). The COM players have 2 'difficulty' settings that can be chosen in the [config file](src/main/resources/config.properties) as well:
1. random - The AI will randomly choose cards in hopes of finding a valid set.
2. smart - The AI will only select cards that form a valid set.

#### Config File
The [config file](src/main/resources/config.properties) allows for changing differnt settings of the game. Implementing it made us make sure that different aspects of the game are modular and dynamic, so that they can be easily changed and modified. Also this made sure that future additions to the code can be easily made.
