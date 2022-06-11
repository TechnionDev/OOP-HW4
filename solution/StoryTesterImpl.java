package solution;

import org.junit.ComparisonFailure;
import provided.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class StoryTesterImpl implements StoryTester {
    /**
     * Runs a given story on an instance of a given class, or an instances of its
     * ancestors. before running the story use must create an instance of the given
     * class.
     *
     * @param story     contains the text of the story to test, the string is
     *                  divided to line using '\n'. each word in a line is separated by space
     *                  (' ').
     * @param testClass the test class which the story should be run on.
     */
    @Override
    public void testOnInheritanceTree(String story, Class<?> testClass) throws Exception {
        if (story == null || testClass == null) {
            throw new IllegalArgumentException();
        }
        String[] sentences = story.split("\n");
        Object instanceTestClass = getNewObject(testClass);
        executeSentence(sentences[0], getAnnotation(sentences[0]), getMethodName(sentences[0]), getArgument(sentences[0]), testClass, instanceTestClass);
        int failures = 0;
        StoryTestExceptionImpl save_first = null;
        Map<Field, Object> instanceBackupMap = backup(instanceTestClass);
        int i = 0;
        for (String sentence : sentences) {
            if (i == 0) { //meaning, it's a 'Given' sentence
                i++;
                continue;
            }
            if (sentence.startsWith("When") && (sentences[i - 1].startsWith("Then"))) {
                //need to start a new backup
                assert instanceBackupMap != null;
                instanceBackupMap.clear();
                instanceBackupMap = backup(instanceTestClass);
            }
            i++;
            try {
                executeSentence(sentence, getAnnotation(sentence), getMethodName(sentence), getArgument(sentence), testClass, instanceTestClass);
            } catch (StoryTestExceptionImpl e) {
                if (save_first == null) {
                    save_first = e;
                }
                failures++;
                restore(instanceTestClass, instanceBackupMap);
            }
        }
        if (failures != 0) {
            save_first.setNumOfFails(failures);
            throw save_first;
        }
    }

    private void restore(Object instanceTestClass, Map<Field, Object> instanceBackupMap) {
        if (null == instanceTestClass || instanceBackupMap.isEmpty())
            return;
        Field[] fields = instanceTestClass.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (!instanceBackupMap.containsKey(field))
                continue; //problem
            field.setAccessible(true);
            Object fieldData = instanceBackupMap.get(field);
            try {
                field.set(instanceTestClass, fieldData);
            } catch (SecurityException | IllegalArgumentException | IllegalAccessException ignored) {
            }
        }
    }

    private Map<Field, Object> backup(Object instanceTestClass) {
        if (null == instanceTestClass) return null;
        Map<Field, Object> map = new HashMap<>();
        Field[] fields = instanceTestClass.getClass().getDeclaredFields();
        for (Field f : fields) {
            try {
                f.setAccessible(true);
                Object o = f.get(instanceTestClass);
                if (o == null) {
                    map.put(f, o);
                    continue;
                }
                if (o instanceof Cloneable) { //try clone
                    Method clone = o.getClass().getDeclaredMethod("clone");
                    clone.setAccessible(true);
                    map.put(f, clone.invoke(o));
                } else { //if there's no clone then try copy constructor
                    Constructor<?> copy_constructor = o.getClass().getDeclaredConstructor(o.getClass());
                    copy_constructor.setAccessible(true);
                    map.put(f, copy_constructor.newInstance(o));
                }
            } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException |
                     InstantiationException e) {
                //eventually, save basic reference
                try {
                    Object o = f.get(instanceTestClass);
                    map.put(f, o);
                } catch (Exception e1) {
                    //e1.printStackTrace(); //shouldn't get here according to the exercise
                }
            }
        }
        return map;
    }

    private void executeSentence(String sentence, String annotation, String methodName, Object arg, Class<?> testClass, Object instanceTestClass) throws Exception {
        Method[] methods = testClass.getDeclaredMethods();
        for (Method met : methods) {
            met.setAccessible(true);
            if (isCompatible(annotation, met, methodName)) {
                try {
                    met.invoke(instanceTestClass, arg);
                } catch (InvocationTargetException e) {
                    //the only method that can throw is Then annotation.
                    if (e.getTargetException().getClass().getSimpleName().equals(ComparisonFailure.class.getSimpleName())) {
                        ComparisonFailure cf = (ComparisonFailure) e.getTargetException();
                        String expected = cf.getExpected(); // <- this is AMAZING!
                        String fail = cf.getActual(); // <- this is AMAZING!
                        throw new StoryTestExceptionImpl(sentence, expected, fail);
                    }
                }
                return;
            }
        }
        if (testClass.getSuperclass() == null) {
            throw annotation.equals("Given") ? new GivenNotFoundException() : annotation.equals("When") ? new WhenNotFoundException() : new ThenNotFoundException();
        }
        executeSentence(sentence, annotation, methodName, arg, testClass.getSuperclass(), instanceTestClass);
    }

    private boolean isCompatible(String annotation, Method met, String methodName) {
        if (annotation.equals("Given") && met.getAnnotation(Given.class) != null && getY(met.getAnnotation(Given.class).value()).equals(methodName)) {
            return true;
        } else if (annotation.equals("When") && met.getAnnotation(When.class) != null && getY(met.getAnnotation(When.class).value()).equals(methodName)) {
            return true;
        } else {
            return annotation.equals("Then") && met.getAnnotation(Then.class) != null && getY(met.getAnnotation(Then.class).value()).equals(methodName);
        }
    }

    private String getY(String methodName) {
        return methodName.substring(0, methodName.lastIndexOf(" "));
    }

    private Object getArgument(String sentence) {
        String temp = sentence.substring(sentence.lastIndexOf(" ") + 1);
        Object val;
        try {
            val = Integer.parseInt(temp);
        } catch (NumberFormatException e) {
            val = temp;
        }
        return val;
    }

    private String getMethodName(String sentence) {
        return sentence.substring(sentence.indexOf(" ") + 1, sentence.lastIndexOf(" "));
    }

    private String getAnnotation(String sentence) {
        return sentence.substring(0, sentence.indexOf(" "));
    }

    private Object getNewObject(Class<?> testClass) {
        try {
            Constructor<?> c = testClass.getDeclaredConstructor();
            c.setAccessible(true);
            return c.newInstance();
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                 InvocationTargetException e) {
            //TODO:: not sure we need to do this
            Object enclosingObject = getNewObject(testClass.getEnclosingClass());
            try {
                Constructor<?> c = testClass.getDeclaredConstructor(enclosingObject.getClass());
                c.setAccessible(true);
                return c.newInstance(enclosingObject);
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                     InvocationTargetException ignored) {
            }
        }
        return null;
    }

    /**
     * Runs a given story on an instance of a given class, or an instances of its
     * ancestors, or its nested class (and their ancestors) as described by the
     * the assignment document. before running the story use must create an instance
     * of the given correct class to run story on.
     *
     * @param story     contains the text of the story to test, the string is
     *                  divided to line using '\n'. each word in a line is separated by space
     *                  (' ').
     * @param testClass the test class which the story should be run on.
     */
    @Override
    public void testOnNestedClasses(String story, Class<?> testClass) throws Exception {
        if (null == story || null == testClass)
            throw new IllegalArgumentException();
        boolean foundGiven = false;
        String given_sentence = story.split("\n")[0];
        if (null != findGivenMethod(given_sentence, testClass)) {
            foundGiven = true;
            testOnInheritanceTree(story, testClass);
        } else {
            Class<?>[] sub_classes = testClass.getDeclaredClasses();
            for (Class<?> sub_class : sub_classes) {
                try {
                    testOnNestedClasses(story, sub_class);
                } catch (GivenNotFoundException e) {
                    continue;
                }
                foundGiven = true;
            }
        }
        if (!foundGiven) throw new GivenNotFoundException();
    }

    private Object findGivenMethod(String given_sentence, Class<?> testClass) {
        if (null == testClass)
            return null;
        Method[] declaredMethods = testClass.getDeclaredMethods();
        for (Method method : declaredMethods) {
            Given ann = method.getAnnotation(Given.class);
            if (null == ann)
                continue;
            String value = ann.value();
            if (isCompatible("Given", method, getMethodName(given_sentence)))
                return method;
        }
        //if reached here, the right 'Given' method is not here, going to find it in super class
        return findGivenMethod(given_sentence, testClass.getSuperclass());
    }
}
