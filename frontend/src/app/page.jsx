import Image from "next/image";
import brainrotImage from "../../images/Brainrot.png";
import tetoImage from "../../images/teto.png";

export default function Home() {
  return (
    <main className="hero">
      <h1>ブレインロット もう止められない</h1>
      <div className="bottom-images">
        <div className="image-slot left">
          <Image src={brainrotImage} alt="brainrot" priority />
        </div>
        <div className="image-slot right">
          <Image src={tetoImage} alt="teto" priority />
        </div>
      </div>
    </main>
  );
}
