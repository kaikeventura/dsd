package com.kaikeventura.dsd;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.graphics.g3d.utils.AnimationController;
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.DebugDrawer;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.badlogic.gdx.physics.bullet.linearmath.btIDebugDraw;
import com.badlogic.gdx.physics.bullet.linearmath.btMotionState;
import net.mgsx.gltf.loaders.glb.GLBLoader;
import net.mgsx.gltf.scene3d.scene.SceneAsset;

public class GameScreen implements Screen {

    // 3D Engine
    public PerspectiveCamera cam;
    public ModelBatch modelBatch;
    public Environment environment;

    // Modelos
    public Model playerModel, groundModel;
    public Model runModel, attackModel; // Modelos adicionais para animações
    public ModelInstance playerInstance, groundInstance;

    // Animação
    private AnimationController animationController;
    private String currentAnimationId = "";

    // PS1 Rendering
    FrameBuffer fbo;
    SpriteBatch spriteBatch;
    TextureRegion fboRegion;
    final int VIRTUAL_WIDTH = 320;
    final int VIRTUAL_HEIGHT = 240;

    // Lógica do Jogador
    PlayerController playerController;
    ThirdPersonCameraController cameraController;

    // Bullet Physics
    btCollisionConfiguration collisionConfig;
    btDispatcher dispatcher;
    btDbvtBroadphase broadphase;
    btConstraintSolver solver;
    btDynamicsWorld dynamicsWorld;
    DebugDrawer debugDrawer;

    btRigidBody playerBody;
    btRigidBody groundBody;
    MyMotionState playerMotionState;

    // Variáveis de Calibração FINAIS
    private final float offsetX = 0f;
    private final float offsetY = -0.9f;
    private final float offsetZ = 0f;
    private final float modelScale = 1.2f;

    // Classe auxiliar para sincronizar física e gráficos
    static class MyMotionState extends btMotionState {
        Matrix4 transform;

        public MyMotionState(Matrix4 transform) {
            this.transform = transform;
        }

        @Override
        public void getWorldTransform(Matrix4 worldTrans) {
            worldTrans.set(transform);
        }

        @Override
        public void setWorldTransform(Matrix4 worldTrans) {
            transform.set(worldTrans);
        }
    }

    public GameScreen() {
        Bullet.init();
    }

    @Override
    public void show() {
        Gdx.input.setCursorCatched(true);

        // 1. ILUMINAÇÃO
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));

        // 2. CÂMERA
        cam = new PerspectiveCamera(67, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        cam.near = 0.1f;
        cam.far = 300f;

        // 3. MODELOS
        DefaultShader.Config config = new DefaultShader.Config();
        config.numBones = 64;
        modelBatch = new ModelBatch(new DefaultShaderProvider(config));

        // Carrega o asset principal (Idle)
        SceneAsset idleAsset = new GLBLoader().load(Gdx.files.internal("knight_idle.glb"));
        playerModel = idleAsset.scene.model;
        playerInstance = new ModelInstance(playerModel);

        // Renomeia a animação de idle para facilitar
        if (playerInstance.animations.size > 0) {
            playerInstance.animations.get(0).id = "idle";
        }

        // Carrega Run e faz o Retargeting
        SceneAsset runAsset = new GLBLoader().load(Gdx.files.internal("knight_run.glb"));
        runModel = runAsset.scene.model;
        addAndRetargetAnimation(runModel, "run");

        // Carrega Attack e faz o Retargeting
        SceneAsset attackAsset = new GLBLoader().load(Gdx.files.internal("knight_attack.glb"));
        attackModel = attackAsset.scene.model;
        addAndRetargetAnimation(attackModel, "attack");

        // 4. ANIMAÇÃO
        animationController = new AnimationController(playerInstance);
        animationController.allowSameAnimation = true; // Permite transições suaves

        ModelBuilder modelBuilder = new ModelBuilder();
        groundModel = modelBuilder.createBox(50f, 1f, 50f, new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(Color.DARK_GRAY)), com.badlogic.gdx.graphics.VertexAttributes.Usage.Position | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);
        groundInstance = new ModelInstance(groundModel);
        groundInstance.transform.setToTranslation(0, -0.5f, 0);

        // 5. SISTEMA DE PIXELS (PS1)
        spriteBatch = new SpriteBatch();
        fbo = new FrameBuffer(Pixmap.Format.RGB565, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, true);

        // 6. CONFIGURAÇÃO DA FÍSICA
        collisionConfig = new btDefaultCollisionConfiguration();
        dispatcher = new btCollisionDispatcher(collisionConfig);
        broadphase = new btDbvtBroadphase();
        solver = new btSequentialImpulseConstraintSolver();
        dynamicsWorld = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfig);
        dynamicsWorld.setGravity(new Vector3(0, -9.8f, 0));

        debugDrawer = new DebugDrawer();
        debugDrawer.setDebugMode(btIDebugDraw.DebugDrawModes.DBG_DrawWireframe);
        dynamicsWorld.setDebugDrawer(debugDrawer);

        // Corpo do Chão
        btCollisionShape groundShape = new btBoxShape(new Vector3(25f, 0.5f, 25f));
        btRigidBody.btRigidBodyConstructionInfo groundInfo = new btRigidBody.btRigidBodyConstructionInfo(0, null, groundShape, Vector3.Zero);
        groundBody = new btRigidBody(groundInfo);
        groundInfo.dispose();
        groundBody.setWorldTransform(groundInstance.transform);
        dynamicsWorld.addRigidBody(groundBody);

        // Corpo do Jogador
        btCollisionShape playerShape = new btCapsuleShape(0.5f, 1.8f);
        Vector3 localInertia = new Vector3();
        playerShape.calculateLocalInertia(1f, localInertia);
        playerMotionState = new MyMotionState(playerInstance.transform);
        btRigidBody.btRigidBodyConstructionInfo playerInfo = new btRigidBody.btRigidBodyConstructionInfo(1f, playerMotionState, playerShape, localInertia);
        playerBody = new btRigidBody(playerInfo);
        playerInfo.dispose();
        playerBody.setAngularFactor(Vector3.Y);
        playerBody.setActivationState(Collision.DISABLE_DEACTIVATION);
        playerInstance.transform.translate(0, 5f, 0);
        playerBody.setWorldTransform(playerInstance.transform);
        dynamicsWorld.addRigidBody(playerBody);

        // 7. CONTROLLERS
        playerController = new PlayerController(playerBody, cam);
        cameraController = new ThirdPersonCameraController(cam, playerInstance);
    }

    // Método Mágico para corrigir as animações
    private void addAndRetargetAnimation(Model sourceModel, String animId) {
        for (com.badlogic.gdx.graphics.g3d.model.Animation anim : sourceModel.animations) {
            anim.id = animId;

            // Retargeting: Atualiza os nós da animação para apontar para os nós da instância do jogador
            for (com.badlogic.gdx.graphics.g3d.model.NodeAnimation nodeAnim : anim.nodeAnimations) {
                com.badlogic.gdx.graphics.g3d.model.Node targetNode = playerInstance.getNode(nodeAnim.node.id, true);
                if (targetNode != null) {
                    nodeAnim.node = targetNode;
                }
            }
            playerInstance.animations.add(anim);
            System.out.println("Animação adicionada e redirecionada: " + animId);
        }
    }

    @Override
    public void render(float delta) {
        // Atualiza os controllers
        playerController.update(delta);
        cameraController.update(delta);

        // Lógica de Animação
        boolean movingNow = Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.D);

        if (movingNow) {
            if (!currentAnimationId.equals("run")) {
                System.out.println("Trocando para RUN");
                animationController.animate("run", -1, 1f, null, 0.2f);
                currentAnimationId = "run";
            }
        } else {
            if (!currentAnimationId.equals("idle")) {
                System.out.println("Trocando para IDLE");
                animationController.animate("idle", -1, 1f, null, 0.2f);
                currentAnimationId = "idle";
            }
        }

        // Atualiza a animação com o delta time
        animationController.update(Gdx.graphics.getDeltaTime());

        // Simulação da física
        dynamicsWorld.stepSimulation(delta, 5, 1f / 60f);

        // Sincronização Física-Visual
        playerBody.getWorldTransform(playerInstance.transform);
        playerInstance.transform.translate(offsetX, offsetY, offsetZ);
        playerInstance.transform.rotate(Vector3.Y, 180f); // Rotação de 180 graus para corrigir a orientação
        playerInstance.transform.scale(modelScale, modelScale, modelScale);

        // Renderização
        fbo.begin();
        Gdx.gl.glViewport(0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        Gdx.gl.glClearColor(0.05f, 0.05f, 0.05f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(cam);
        modelBatch.render(playerInstance, environment);
        modelBatch.render(groundInstance, environment);
        modelBatch.end();

        debugDrawer.begin(cam);
        dynamicsWorld.debugDrawWorld();
        debugDrawer.end();

        fbo.end();

        // Desenha na tela grande
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        fboRegion = new TextureRegion(fbo.getColorBufferTexture());
        fboRegion.flip(false, true);

        spriteBatch.begin();
        spriteBatch.draw(fboRegion, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        spriteBatch.end();
    }

    @Override
    public void resize(int width, int height) {
    }

    @Override
    public void pause() {
        Gdx.input.setCursorCatched(false);
    }

    @Override
    public void resume() {
        Gdx.input.setCursorCatched(true);
    }

    @Override
    public void hide() {
        Gdx.input.setCursorCatched(false);
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        playerModel.dispose();
        if (runModel != null) runModel.dispose();
        if (attackModel != null) attackModel.dispose();
        groundModel.dispose();
        fbo.dispose();
        spriteBatch.dispose();
        debugDrawer.dispose();

        dynamicsWorld.dispose();
        solver.dispose();
        broadphase.dispose();
        dispatcher.dispose();
        collisionConfig.dispose();
        playerBody.dispose();
        groundBody.dispose();
    }
}
