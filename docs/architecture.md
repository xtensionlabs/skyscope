# Brian — OOP Code Walkthrough

Open **src/main/java/com/example/airplane/model/Aircraft.java**. Keep the editor on this one file and move through lines **9–13**, **29–32**, then **47–56**.

## Start at lines 9–13 — abstraction and encapsulation

**Say:**

> “I’m using Aircraft as one compact example of how we apply object-oriented programming. Aircraft is abstract, so it describes the general idea of an aircraft rather than one specific plane. The key, name, and seat count are private fields. That means other classes cannot reach in and change them directly. They are also final, so they are set when the object is created and do not change afterwards. Keeping the data inside the class is encapsulation.”

**Point out:** the word abstract on the class declaration, then the private final fields.

## Move to lines 29–32 — required behavior

**Say:**

> “Aircraft also declares category and cabinCrew as abstract methods. The base class knows that every aircraft has a category and a minimum crew requirement, but it does not decide the values here. Each specific aircraft type provides those answers. This is abstraction: the parent defines what its children must provide, while leaving the type-specific details to them.”

**Point out:** there is no method body after these declarations. The concrete aircraft classes supply the implementation.

## Move to lines 47–56 — inheritance, polymorphism, and the factory

**Say:**

> “This method takes an aircraft key and creates the matching type. For example, an A320 becomes a NarrowBody, an E190 becomes a RegionalJet, and an A330 becomes a WideBody. Those classes extend Aircraft, so they inherit the shared aircraft structure and provide their own category and crew behavior. That is inheritance.”

> “Notice that the factory returns the general Aircraft type even though it creates different specific types. The rest of the program can work with an Aircraft without needing to know which subtype it has. When it asks for category or cabinCrew, Java uses the implementation from the actual subtype. The same method call can produce the right answer for each type; that is polymorphism.”

**Live app cue (during this explanation):** Switch briefly to the running app's **Board View**. Open the detail drawer
for **EK720** (B777-300ER), then **TK606** (A320), and point to each flight's **AIRCRAFT** detail.

**Say as you show the details:**

> “Here we can see the aircraft information in the app. The details view asks the flight's aircraft for a description.
> Both types use the shared `describe()` method, but `category()` comes from the specific aircraft type. That's why
> the name, category, and seat count shown here match the selected aircraft. This is polymorphism in action.”

**Point out:** The board calls `f.aircraft().describe()`. `Aircraft.describe()` calls `category()`, and Java uses the
implementation from the actual subtype. Return to `Aircraft.java` and point out that `Aircraft.of` returns the shared
`Aircraft` type while its switch creates `NarrowBody`, `RegionalJet`, and `WideBody` objects. Their `extends`
declarations and method implementations are in the subtype files.

## Close

**Say:**

> “So this one class gives us a shared abstraction, private data, and a factory for creating specialized aircraft. The subclasses reuse the common structure and supply the behavior that differs. That keeps aircraft handling consistent while still representing each type accurately.”
