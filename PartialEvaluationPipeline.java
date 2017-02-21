package ec.app.Ayden;
import java.util.Stack;
import ec.*;
import ec.util.*;
import ec.util.Parameter;
import ec.gp.*;
import ec.gp.koza.*;
import ec.simple.*;

public class PartialEvaluationPipeline extends GPBreedingPipeline
{
    private static final long serialVersionUID = 1;
    
    public static final String P_MUTATION = "mutate";
    public static final String P_BUILDER = "build";
    public static final int INDS_PRODUCED = 1;
    public static final int NUM_SOURCES = 1;

    /** How the pipeline chooses a subtree to mutate */
    public GPNodeSelector nodeselect;

    /** How the pipeline builds a new subtree */
    public GPNodeBuilder builder;

    /** Is our tree fixed?  If not, this is -1 */
    int tree;

    /** Problem used to evaluate individuals **/
    Problem p_problem;

    public Parameter defaultBase() { return GPKozaDefaults.base().push(P_MUTATION); }

    public int numSources() { return NUM_SOURCES; }

    public Object clone()
    {
        PartialEvaluationPipeline c = (PartialEvaluationPipeline)(super.clone());

        // deep-cloned stuff
        c.nodeselect = (GPNodeSelector)(nodeselect.clone());

        return c;
    }

    public void setup(final EvolutionState state, final Parameter base)
    {
        super.setup(state,base);


        // create a new problem that will be used to get the new fitness of the altered trees.      
        String P_PROBLEM = "problem";
        Parameter foo = new Parameter("eval");
        // load the problem
        p_problem = (Problem)(state.parameters.getInstanceForParameter(
            foo.push(P_PROBLEM),null,Problem.class));
        p_problem.setup(state,foo.push(P_PROBLEM));

        
        Parameter def = defaultBase();
        Parameter p = base.push(P_NODESELECTOR).push(""+0);
        Parameter d = def.push(P_NODESELECTOR).push(""+0);

        nodeselect = (GPNodeSelector)
        (state.parameters.getInstanceForParameter(
            p,d, GPNodeSelector.class));
        nodeselect.setup(state,p);

        p = base.push(P_BUILDER).push(""+0);
        d = def.push(P_BUILDER).push(""+0);

        builder = (GPNodeBuilder)
        (state.parameters.getInstanceForParameter(
            p,d, GPNodeBuilder.class));
        builder.setup(state,p);
        
        tree = TREE_UNFIXED;
        if (state.parameters.exists(base.push(P_TREE).push(""+0),
            def.push(P_TREE).push(""+0)))
        {
            tree = state.parameters.getInt(base.push(P_TREE).push(""+0),
                def.push(P_TREE).push(""+0),0);
            if (tree==-1)
                state.output.fatal("Tree fixed value, if defined, must be >= 0");
        }
    }

    // goes through every node in the tree
    void transverse(EvolutionState state, int thread, GPIndividual subject) {
        // make sure there is a tree here
        
        if (subject == null) return;

        GPTree tree1 = subject.trees[0];
        GPNode root = tree1.child;

        if (root == null) return;

        // a stack to hold the trees while we transverse them
        Stack<GPNode> stack = new Stack<GPNode>();
        GPNode node = root;
        GPNode swap;

        GPNodeParent saveparent;
        byte saveargposition;

        double origfitness = subject.fitness.fitness();
        double newfitness = 9999999;


        // head to the leaves of the tree
        while (node.children.length > 0) {
            stack.push(node);
            node = node.children[0];
        }

        // visit every node
        while (stack.size() > 0) {
            node = stack.pop();
            // testing node
            // we will swap the current node with the root node
            swap = node;
            subject.evaluated = false;
            
            // store infomation from the swaped node so we can swap back
            saveparent = swap.parent;
            saveargposition = swap.argposition;

            // swap the current node with the root
            subject.trees[0].child = swap;
            swap.parent = root.parent;
            swap.argposition = root.argposition;
            // evaluate this new configuration
            ((SimpleProblemForm)p_problem).evaluate(state, subject, 0, thread);
            // see if its an improvement
            newfitness = subject.fitness.fitness();
            if(newfitness > origfitness) {
                // we did it
                System.out.println("wow, it's working!!!");
                return;
            } else {
                // reset everything for the next one
                swap.parent = saveparent;
                swap.argposition = saveargposition;
                subject.trees[0].child = root;
            }

            // test the node here
            if (node.children.length > 1) {
                node = node.children[1];
                while (node.children.length > 0) {
                    stack.push(node);
                    node = node.children[0];
                }
            }
        }
    }

    public int produce(final int min, 
        final int max, 
        final int start,
        final int subpopulation,
        final Individual[] inds,
        final EvolutionState state,
        final int thread) 
    {
        // grab individuals from our source and stick 'em right into inds.
        // we'll modify them from there
        int n = sources[0].produce(min,max,start,subpopulation,inds,state,thread);

        // I have never seen this code run
        // should we bother?
        if (!state.random[thread].nextBoolean(likelihood)) 
            return reproduce(n, start, subpopulation, inds, state, thread, false);  // DON'T produce children from source -- we already did


        GPInitializer initializer = ((GPInitializer)state.initializer);

        // loop through all the trees that need mutating
        // now let's mutate 'em
        for(int q=start; q < n+start; q++)
        {
            GPIndividual i = (GPIndividual)inds[q];

            //System.out.println(i.fitness.fitnessToStringForHumans());

            // seems like there is always only one tree
            int t = 0;

            //tree.child would pick the root

            GPTree tree1 = i.trees[0];
            GPNode root = tree1.child;
            // grab the first child if there is one
            if(root.children.length > 0) {

                transverse(state, thread, i);
            }

        // add the new individual, replacing its previous source
            inds[q] = i;
        }
        return n;
    }
}
