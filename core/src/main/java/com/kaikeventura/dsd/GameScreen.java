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
import com.badlogic.gdx.graphics.g3d.model.Node;
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
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import net.mgsx.gltf.loaders.glb.GLBLoader;
import net.mgsx.gltf.scene3d.scene.SceneAsset;

import java.util.ArrayList;
import java.util.List;

public class GameScreen implements Screen {

    // 3D Engine
    public PerspectiveCamera cam;
    public ModelBatch modelBatch;
    public Environment environment;

    // Modelos
    public Model playerModel, groundModel, swordModel;
    // Lista para guardar referências e dar dispose depois
    private List<Model> animationModels = new ArrayList<>();

    public ModelInstance playerInstance, groundInstance, swordInstance;
    private Node handNode;

    // Variáveis de Calibração da Espada
    private float swordScale = 1.0f;
    private float rotX = 0, rotY = 0, rotZ = 0;
    private float posX = 0, posY = 0, posZ = 0;

    // Animação
    private AnimationController animationController;
    private String currentAnimationId = "";
    private boolean isAttacking = false;

    // PS1 Rendering
    FrameBuffer fbo;
    SpriteBatch spriteBatch;
    TextureRegion fboRegion;
    final int VIRTUAL_WIDTH = 320;
    final int VIRTUAL_HEIGHT = 240;
    Viewport viewport; // Viewport para manter a proporção na tela cheia

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

        // Carrega Animações Extras com Retargeting
        loadAndAppendAnimation("knight-walk.glb", "walk_fwd");
        loadAndAppendAnimation("knight-walk-back.glb", "walk_back");
        loadAndAppendAnimation("knight-walk-left.glb", "walk_left");
        loadAndAppendAnimation("knight-walk-right.glb", "walk_right");
        loadAndAppendAnimation("knight-jump.glb", "jump");
        loadAndAppendAnimation("knight_attack.glb", "attack");

        // Carrega a Espada
        try {
            SceneAsset swordAsset = new GLBLoader().load(Gdx.files.internal("sword.glb"));
            swordModel = swordAsset.scene.model;
            swordInstance = new ModelInstance(swordModel);
        } catch (Exception e) {
            System.err.println("Erro ao carregar sword.glb: " + e.getMessage());
        }

        // Procura o osso da mão
        handNode = playerInstance.getNode("mixamo.com:RightHand", true);
        if (handNode == null) {
            handNode = playerInstance.getNode("RightHand", true); // Tenta nome alternativo
        }
        if (handNode == null) {
            System.out.println("ERRO CRÍTICO: Nó da mão não encontrado no modelo!");
            // Lista nós para debug se necessário
            // for(Node n : playerInstance.nodes) System.out.println(n.id);
        } else {
            System.out.println("Sucesso: Nó da mão encontrado: " + handNode.id);
        }

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

        // Inicializa o Viewport para manter a proporção 320x240 na tela cheia
        viewport = new FitViewport(VIRTUAL_WIDTH, VIRTUAL_HEIGHT);

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

    // Método auxiliar para carregar e fazer retargeting
    private void loadAndAppendAnimation(String fileName, String internalName) {
        try {
            SceneAsset asset = new GLBLoader().load(Gdx.files.internal(fileName));
            Model model = asset.scene.model;
            animationModels.add(model); // Guarda para dispose

            if (model.animations.size > 0) {
                com.badlogic.gdx.graphics.g3d.model.Animation anim = model.animations.get(0);
                anim.id = internalName;

                // Retargeting
                for (com.badlogic.gdx.graphics.g3d.model.NodeAnimation nodeAnim : anim.nodeAnimations) {
                    com.badlogic.gdx.graphics.g3d.model.Node targetNode = playerInstance.getNode(nodeAnim.node.id, true);
                    if (targetNode != null) {
                        nodeAnim.node = targetNode;
                    }
                }
                playerInstance.animations.add(anim);
                System.out.println("Animação carregada: " + internalName + " de " + fileName);
            }
        } catch (Exception e) {
            System.err.println("Erro ao carregar animação: " + fileName);
            e.printStackTrace();
        }
    }

    @Override
    public void render(float delta) {
        // Atualiza a câmera sempre
        cameraController.update(delta);

        // --- DEBUG DE CALIBRAÇÃO DA ESPADA ---
        if (Gdx.input.isKeyPressed(Input.Keys.NUM_1)) rotX += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.NUM_2)) rotX -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.NUM_3)) rotY += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.NUM_4)) rotY -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.NUM_5)) rotZ += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.NUM_6)) rotZ -= 1f;

        if (Gdx.input.isKeyPressed(Input.Keys.UP)) posY += 0.01f;
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) posY -= 0.01f;
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) posX -= 0.01f;
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) posX += 0.01f;
        if (Gdx.input.isKeyPressed(Input.Keys.O)) posZ -= 0.01f;
        if (Gdx.input.isKeyPressed(Input.Keys.P)) posZ += 0.01f;

        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            System.out.println("CALIBRAÇÃO ATUAL: ");
            System.out.println("Rot: " + rotX + ", " + rotY + ", " + rotZ);
            System.out.println("Pos: " + posX + ", " + posY + ", " + posZ);
        }
        // -------------------------------------

        // Lógica de Ataque e Movimento
        if (!isAttacking) {
            // Só permite mover se não estiver atacando
            playerController.update(delta);

            // Verifica se quer atacar
            if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
                isAttacking = true;
                currentAnimationId = "attack";

                // Para o movimento físico ao atacar
                playerBody.setLinearVelocity(new Vector3(0, playerBody.getLinearVelocity().y, 0));

                animationController.animate("attack", 1, 1f, new AnimationController.AnimationListener() {
                    @Override
                    public void onEnd(AnimationController.AnimationDesc animation) {
                        isAttacking = false;
                        // Volta para idle suavemente
                        animationController.animate("idle", -1, 1f, null, 0.2f);
                        currentAnimationId = "idle";
                    }

                    @Override
                    public void onLoop(AnimationController.AnimationDesc animation) {
                    }
                }, 0.1f);
            } else {
                // Lógica de Animação de Movimento (Prioridade)
                String animToPlay = "idle";
                int loopCount = -1; // Infinito por padrão

                if (Gdx.input.isKeyPressed(Input.Keys.SPACE)) {
                    animToPlay = "jump";
                    loopCount = 1; // Toca uma vez
                } else if (Gdx.input.isKeyPressed(Input.Keys.W)) {
                    animToPlay = "walk_fwd";
                } else if (Gdx.input.isKeyPressed(Input.Keys.S)) {
                    animToPlay = "walk_back";
                } else if (Gdx.input.isKeyPressed(Input.Keys.A)) {
                    animToPlay = "walk_left";
                } else if (Gdx.input.isKeyPressed(Input.Keys.D)) {
                    animToPlay = "walk_right";
                }

                if (!currentAnimationId.equals(animToPlay)) {
                    float transitionTime = animToPlay.equals("jump") ? 0.1f : 0.2f;
                    animationController.animate(animToPlay, loopCount, 1f, null, transitionTime);
                    currentAnimationId = animToPlay;
                }

                // Reset de pulo se necessário
                if (currentAnimationId.equals("jump") && animationController.current != null && animationController.current.time >= animationController.current.duration) {
                     if (animationController.current.loopCount == 1) {
                         currentAnimationId = "";
                     }
                }
            }
        }

        animationController.update(Gdx.graphics.getDeltaTime());
        dynamicsWorld.stepSimulation(delta, 5, 1f / 60f);

        playerBody.getWorldTransform(playerInstance.transform);
        playerInstance.transform.translate(offsetX, offsetY, offsetZ);
        playerInstance.transform.rotate(Vector3.Y, 180f);
        playerInstance.transform.scale(modelScale, modelScale, modelScale);

        // --- ATUALIZAÇÃO DA ESPADA ---
        if (swordInstance != null && handNode != null) {
            swordInstance.transform.set(playerInstance.transform);
            swordInstance.transform.mul(handNode.globalTransform);

            swordInstance.transform.rotate(Vector3.X, rotX);
            swordInstance.transform.rotate(Vector3.Y, rotY);
            swordInstance.transform.rotate(Vector3.Z, rotZ);
            swordInstance.transform.translate(posX, posY, posZ);
            swordInstance.transform.scale(swordScale, swordScale, swordScale);
        }

        // Renderização no FBO (320x240)
        fbo.begin();
        Gdx.gl.glViewport(0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        Gdx.gl.glClearColor(0.05f, 0.05f, 0.05f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(cam);
        modelBatch.render(playerInstance, environment);
        if (swordInstance != null) modelBatch.render(swordInstance, environment); // Renderiza a espada
        modelBatch.render(groundInstance, environment);
        modelBatch.end();

        debugDrawer.begin(cam);
        dynamicsWorld.debugDrawWorld();
        debugDrawer.end();

        fbo.end();

        // Desenha na tela grande usando o Viewport para corrigir a proporção
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        viewport.apply(); // Aplica o viewport (define glViewport e câmera 2D)

        fboRegion = new TextureRegion(fbo.getColorBufferTexture());
        fboRegion.flip(false, true);

        spriteBatch.setProjectionMatrix(viewport.getCamera().combined); // Usa a matriz do viewport
        spriteBatch.begin();
        // Desenha ocupando todo o mundo virtual do viewport (320x240)
        spriteBatch.draw(fboRegion, 0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        spriteBatch.end();
    }

    @Override
    public void resize(int width, int height) {
        // Atualiza o viewport quando a janela muda de tamanho
        viewport.update(width, height, true); // true centraliza a câmera
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
        if (swordModel != null) swordModel.dispose();

        for (Model m : animationModels) {
            m.dispose();
        }
        animationModels.clear();

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
